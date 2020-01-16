
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using Serilog;
using Bdeploy.Shared;
using System.Text;
using Serilog.Events;
using System;

namespace Bdeploy.Launcher
{
    /// <summary>
    /// Responsible for starting the Java Launcher as well as for applying updates.
    /// </summary>
    public class AppLauncher
    {
        // The main class of the launcher to execute
        public static readonly string MAIN_CLASS = "io.bdeploy.launcher.cli.LauncherCli";

        /// The full path to the current working directory of the launcher
        private static readonly string LAUNCHER = Utils.GetWorkingDir();

        // The full path to the embedded JRE executable to use
        private static readonly string JRE = Path.Combine(LAUNCHER, "jre", "bin", "javaw.exe");

        // The full path to an optional property files containing arguments for the JVM
        private static readonly string JVM_PROPERTIES = Path.Combine(LAUNCHER, "bin", "launcher.properties");

        // The full path to the directory containing the JAR files
        private static readonly string LIB = Path.Combine(LAUNCHER, "lib");

        // The full path to the directory where to store updates and backups
        private static readonly string UPDATES = Path.Combine(LAUNCHER, "updates");

        /// Lock file that is created to avoid that multiple launchers install updates simultaneously
        private static readonly string UPDATE_LOCK = Path.Combine(UPDATES, ".lock");

        // The full path of the directory where the launcher stores the next version
        private static readonly string UPDATES_NEXT = Path.Combine(UPDATES, "next");

        // The full path to the directory where we store backups
        private static readonly string BACKUP = Path.Combine(UPDATES, "backup");

        /// <summary>
        /// Event that is raised when installing the update failed.
        /// </summary>
        public event EventHandler<RetryCancelEventArgs> UpdateFailed;

        /// <summary>
        /// Event that is raised when the installer is waiting for another installation to complete.
        /// </summary>
        public event EventHandler<CancelEventArgs> UpdateWaiting;

        /// <summary>
        /// Event that is raised when the update is starting.
        /// </summary>
        public event EventHandler<object> StartUpdating;

        // The full path of the bdeploy file to launch
        private readonly string application;

        // The deserialized application descriptor
        private ClickAndStartDescriptor descriptor;

        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="application">The bdeploy file to pass to the companion script</param>
        public AppLauncher(string application)
        {
            this.application = application;
        }

        /// <summary>
        /// Launches the minion and waits for the termination
        /// </summary>
        /// <returns> Exit code of the minion.</returns>
        public int Start()
        {
            // Descriptor must be existing and valid
            if (!ValidateDescriptor())
            {
                return -1;
            }

            // The embedded JRE must be valid
            if (!ValidateEmbeddedJre())
            {
                return -2;
            }
            Log.Information("Requesting to start application {0} of instance {1}/{2}", descriptor.ApplicationId, descriptor.GroupId, descriptor.InstanceId);

            // Build arguments to pass to the application
            StringBuilder builder = new StringBuilder();
            AppendCustomJvmArguments(builder);

            // Append classpath and mandatory arguments of application
            builder.AppendFormat("-cp \"{0}\" ", Path.Combine(LIB, "*"));
            builder.AppendFormat("{0} ", MAIN_CLASS);
            builder.AppendFormat("\"{0}\" ", application);
            builder.AppendFormat("\"--updateDir={0}\" ", UPDATES);
            if (Log.IsEnabled(LogEventLevel.Debug))
            {
                Log.Debug("Starting launcher with arguments: {0}", builder.ToString());
            }

            // Startup minion and wait for termination
            using (Process process = new Process())
            {
                process.StartInfo.FileName = JRE;
                process.StartInfo.CreateNoWindow = true;
                process.StartInfo.UseShellExecute = false;
                process.StartInfo.Arguments = builder.ToString();
                process.StartInfo.WorkingDirectory = LAUNCHER;
                process.StartInfo.ErrorDialog = false;
                process.StartInfo.RedirectStandardOutput = true;
                process.StartInfo.RedirectStandardError = true;
                process.Start();

                // Write log file about the startup
                Log.Information("Launcher started. PID = {0}", process.Id);
                Log.Information("Output of launcher is written to a separate log file.");
                Log.Information("See {0} for more details.", GetAppLoggerName());

                // Capture output and write to logfile
                Task logTask = Task.Run(() => CopyToLog(process));

                // Wait until the process terminates
                process.WaitForExit();
                int exitCode = process.ExitCode;
                Log.Information("Launcher terminated. ExitCode = {0}", exitCode);
                return exitCode;
            }
        }

        /// <summary>
        /// Applies the available updates.
        /// </summary>
        public bool ApplyUpdates()
        {
            FileStream lockStream = null;
            try
            {
                // Try to get an exclusive update lock
                lockStream = GetUpdateLock();
                if (lockStream == null)
                {
                    Log.Information("User canceled installation of updates. Exiting application.");
                    return false;
                }
                // Notify that the update is now starting. waiting screen if we have one
                StartUpdating.Invoke(this, new object());
                Log.Information("Update lock successfully acquired. Starting update. ");

                // Check that we have something to update
                if (!Directory.Exists(UPDATES_NEXT))
                {
                    Log.Information("Updates have already been installed. Restarting application.");
                    return true;
                }

                // Backup and update the application
                RetryCancelMode result = RetryCancelMode.RETRY;
                while (result == RetryCancelMode.RETRY)
                {
                    if (BackupAndUpdate())
                    {
                        Log.Information("Updates successfully applied. Restarting application.");
                        return true;
                    }
                    RetryCancelEventArgs retryEvent = new RetryCancelEventArgs();
                    UpdateFailed.Invoke(this, retryEvent);
                    result = retryEvent.Result;
                }

                // Backup and update failed. User canceled operation
                Log.Information("Update has been canceled by the user.");
                Log.Information("Restore application from backup.");

                // Copy all files back from the backup to the working directory
                FileHelper.CopyDirectory(BACKUP, LAUNCHER);
                Log.Information("Application restored. Terminating application.");
                return false;
            }
            finally
            {
                // Release lock
                if (lockStream != null)
                {
                    lockStream.Dispose();
                }
                FileHelper.DeleteFile(UPDATE_LOCK);
            }
        }

        /// <summary>
        /// Acquire an exclusive lock to apply updates. 
        /// </summary>
        /// <returns></returns>
        private FileStream GetUpdateLock()
        {
            // First directly try to get an update lock
            FileStream lockStream = FileHelper.CreateLockFile(UPDATE_LOCK);
            if (lockStream != null)
            {
                return lockStream;
            }

            // Inform the user that we are waiting for the lock
            Log.Information("Waiting to get exclusive lock to apply the updates.");
            CancelEventArgs cancelEvent = new CancelEventArgs();
            UpdateWaiting.Invoke(this, cancelEvent);
            return FileHelper.WaitForExclusiveLock(UPDATE_LOCK, 500, () => cancelEvent.Result);
        }

        /// <summary>
        /// Creates a backup of the current version and copies the new files back to the working directory.
        /// </summary>
        private bool BackupAndUpdate()
        {
            try
            {
                // Cleanup previously created backups
                FileHelper.DeleteDir(BACKUP);
                Directory.CreateDirectory(BACKUP);

                // We first try to check if the JRE is in use before we start moving all files
                if (File.Exists(JRE) && FileHelper.IsFileLocked(JRE))
                {
                    Log.Error("JRE executable is locked by another application.");
                    Log.Error("Update cannot be applied.");
                    return false;
                }

                // Move entire launcher into the backup folder
                Log.Information("Moving files to backup directory.");
                FileHelper.MoveDirectory(LAUNCHER, BACKUP, new string[] { "updates" });

                // Copy all files from the update directory to the working directory
                Log.Information("Copy updated files to working directory.");
                FileHelper.CopyDirectory(UPDATES_NEXT, LAUNCHER);

                // Cleanup files in update directory
                Log.Information("Delete update directory.");
                FileHelper.DeleteDir(UPDATES_NEXT);
                return true;
            }
            catch (Exception ex)
            {
                Log.Error(ex, "Failed to apply updates.");
                return false;
            }
        }

        /// <summary>
        /// Restarts the launcher application.
        /// </summary>
        public bool Restart()
        {
            string executable = Process.GetCurrentProcess().MainModule.FileName;
            string argument = string.Format("\"{0}\"", application);
            Log.Information("Restarting launcher: {0} {1}", executable, argument);

            try
            {
                int pid = Utils.RunProcess(executable, argument);
                Log.Information("Updated launcher running with PID: {0}", pid);
                return true;
            }
            catch (Exception ex)
            {
                Log.Error(ex, "Failed to start launcher after update.");
                Log.Information("Aborting and terminating application.");
                return false;
            }
        }

        /// <summary>
        /// Checks that the given file point to a valid application descriptor
        /// </summary>
        private bool ValidateDescriptor()
        {
            // File must exist 
            if (!File.Exists(application))
            {
                Log.Fatal("Application descriptor '{0}' not found.", application);
                Log.Information("Exiting application.");
                return false;
            }

            // We must be able to deserialize the content
            descriptor = ClickAndStartDescriptor.FromFile(application);
            if (descriptor == null)
            {
                Log.Fatal("Cannot deserialize application descriptor.", application);
                Log.Information("Exiting application.");
                return false;
            }
            return true;
        }

        /// <summary>
        /// Checks that the embedded JRE is working.
        /// </summary>
        public bool ValidateEmbeddedJre()
        {
            // JRE must exist 
            if (!File.Exists(JRE))
            {
                Log.Fatal("Embedded JRE '{0}' not found.", JRE);
                Log.Information("Exiting application.");
                return false;
            }

            // Try to launch it
            int returnCode = Utils.RunProcessAndWait(JRE, "-version");
            if (returnCode != 0)
            {
                Log.Fatal("Embedded JRE terminated with exit code {0}", returnCode);
                Log.Fatal("JRE located in {0} is corrupt or missing", JRE);
                Log.Information("Exiting application.");
                return false;
            }
            return true;
        }

        /// <summary>
        /// Reads output from the given process and writes it to the logger as DEBUG message.
        /// </summary>
        private void CopyToLog(Process process)
        {
            // Create special logger that writes to a separate file
            string path = Path.Combine(PathProvider.GetLogsDir(), GetAppLoggerName());
            ILogger appLogger = LogFactory.GetAppLogger(path);

            // Log output for debugging purpose
            string line;
            StreamReader reader = process.StandardOutput;
            while ((line = reader.ReadLine()) != null)
            {
                appLogger.Information(line);
            }
        }

        /// <summary>
        /// Returns the name of the log file used by the launcher.
        /// </summary>
        /// <returns></returns>
        private string GetAppLoggerName()
        {
            return descriptor.ApplicationId + "-log.txt";
        }

        /// <summary>
        /// Appends custom JVM arguments defined in the properties file
        /// </summary>
        /// <param name="builder"></param>
        private void AppendCustomJvmArguments(StringBuilder builder)
        {
            bool hasXmX = false;
            if (File.Exists(JVM_PROPERTIES))
            {
                foreach (string line in File.ReadAllLines(JVM_PROPERTIES, UTF8Encoding.UTF8))
                {
                    // Ignore comments
                    string arg = line.Trim();
                    if (arg.StartsWith("#"))
                    {
                        continue;
                    }

                    // Check if we have a max memory setting
                    if (arg.StartsWith("-Xmx"))
                    {
                        hasXmX = true;
                    }

                    builder.Append(arg);
                    builder.Append(" ");
                }
            }

            // Ensure that a memory limit is configured
            if (!hasXmX)
            {
                builder.Append("-Xmx256m ");
            }
        }

    }
}
