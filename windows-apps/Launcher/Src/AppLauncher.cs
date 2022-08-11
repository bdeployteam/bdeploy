using Bdeploy.Shared;
using Serilog;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.Serialization.Json;
using System.Text;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Responsible for starting the Java Launcher as well as for applying updates.
    /// </summary>
    public class AppLauncher : ClickAndStartLauncher {

        // The full path to the directory where to store updates and backups
        private static readonly string UPDATES = Path.Combine(LAUNCHER, "updates");

        /// Lock file that is created to avoid that multiple launchers install updates simultaneously
        private static readonly string UPDATE_LOCK = Path.Combine(UPDATES, ".lock");

        /// Marker file that is created by the Java launcher to notify that it is applying updates
        private static readonly string UPDATE_JAVA_LOCK = Path.Combine(UPDATES, ".updating");

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

        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="clickAndStartFile">The .bdeploy file to pass to the companion script</param>
        public AppLauncher(string clickAndStartFile) : base(clickAndStartFile) {
        }

        /// <summary>
        /// Starts the LauncherCli in order to launch the application described by the ClickAndStart file.
        /// </summary>
        /// <param name="args">Arguments to pass to the application</param>
        /// <returns> Exit code of the minion.</returns>
        public int Start(string[] args) {
            // Descriptor must be existing and valid
            if (!ValidateDescriptor()) {
                return -1;
            }

            Log.Information("Requesting to start application {0} of instance {1}/{2}", descriptor.ApplicationId, descriptor.GroupId, descriptor.InstanceId);

            // Build arguments to pass to the application
            StringBuilder builder = new StringBuilder();
            AppendCustomJvmArguments(builder);

            // Append classpath and mandatory arguments of application
            builder.AppendFormat("-cp \"{0}\" ", Path.Combine(LIB, "*"));
            builder.AppendFormat("{0} ", MAIN_CLASS);
            builder.AppendFormat("launcher ");
            builder.AppendFormat("\"--launch={0}\" ", clickAndStartFile);
            builder.AppendFormat("\"--homeDir={0}\" ", HOME);
            builder.AppendFormat("\"--updateDir={0}\" ", UPDATES);

            // All arguments after the -- separator are passed to application that the Java launcher is starting
            // All arguments before are forwarded to the Java launcher. They will not be passed to the launched application
            List<string> appArgs = new List<string>();
            List<string> launcherArgs = new List<string>();
            int argSeparator = Array.IndexOf(args, "--");
            if (argSeparator != -1) {
                launcherArgs.AddRange(args.Take(argSeparator));
                appArgs.AddRange(args.Skip(argSeparator + 1));
            } else {
                launcherArgs = args.ToList();
            }

            // Arguments that will be forwarded to the final application are Base64 encoded and then 
            // serialized as JSON list. Using that approach we can simply decode the value and convert the JSON to a list in Java
            // without the need to worry about proper escaping those values while passing it to the new process
            if (appArgs.Count != 0) {
                MemoryStream stream = new MemoryStream();
                DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(List<string>));
                ser.WriteObject(stream, appArgs);

                string argsEncoded = Convert.ToBase64String(stream.ToArray());
                builder.AppendFormat("\"--appendArgs={0}\" ", argsEncoded);
            }

            // Arguments for the launcher are simply added as they have been passed
            foreach (string appArg in launcherArgs) {
                builder.AppendFormat("\"{0}\" ", appArg);
            }

            return StartLauncher(builder.ToString());
        }

        /// <summary>
        /// Applies the available updates.
        /// </summary>
        public bool ApplyUpdates() {
            FileStream lockStream = null;
            try {
                // Try to get an exclusive update lock
                Directory.CreateDirectory(UPDATES);
                lockStream = GetUpdateLock();
                if (lockStream == null) {
                    Log.Information("User canceled installation of updates. Exiting application.");
                    return false;
                }
                // Notify that the update is now starting. waiting screen if we have one
                StartUpdating.Invoke(this, new object());
                Log.Information("Update lock successfully acquired. Starting update. ");

                // Check that we have something to update
                if (!Directory.Exists(UPDATES_NEXT)) {
                    Log.Information("No updates available or updates have already been installed. Restarting application.");
                    return true;
                }

                // Backup and update the application
                RetryCancelMode result = RetryCancelMode.RETRY;
                while (result == RetryCancelMode.RETRY) {
                    if (BackupAndUpdate()) {
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
            } finally {
                // Release lock
                if (lockStream != null) {
                    lockStream.Dispose();
                }
                FileHelper.DeleteFile(UPDATE_LOCK);
            }
        }

        /// <summary>
        /// Acquire an exclusive lock to apply updates. 
        /// </summary>
        /// <returns></returns>
        private FileStream GetUpdateLock() {
            // First directly try to get an update lock
            FileStream lockStream = FileHelper.CreateLockFile(UPDATE_LOCK);
            if (lockStream != null) {
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
        private bool BackupAndUpdate() {
            try {
                // Cleanup previously created backups
                FileHelper.DeleteDir(BACKUP);
                Directory.CreateDirectory(BACKUP);

                // We first try to check if the JRE is in use before we start moving all files
                if (File.Exists(JRE) && FileHelper.IsFileLocked(JRE)) {
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

                // Cleanup update marker
                FileHelper.DeleteFile(UPDATE_JAVA_LOCK);
                return true;
            } catch (Exception ex) {
                Log.Error(ex, "Failed to apply updates.");
                return false;
            }
        }

        /// <summary>
        /// Restarts the launcher application.
        /// </summary>
        public bool Restart(string[] args) {
            string executable = Process.GetCurrentProcess().MainModule.FileName;

            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("\"{0}\" ", clickAndStartFile);
            foreach (string appArg in args)
            {
                builder.AppendFormat("\"{0}\" ", appArg);
            }

            string argument = builder.ToString();
            Log.Information("Restarting launcher: {0} {1}", executable, argument);

            try {
                int pid = Utils.RunProcess(executable, argument);
                Log.Information("Updated launcher running with PID: {0}", pid);
                return true;
            } catch (Exception ex) {
                Log.Error(ex, "Failed to start launcher after update.");
                Log.Information("Aborting and terminating application.");
                return false;
            }
        }


    }
}
