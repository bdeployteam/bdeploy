using Bdeploy.Shared;
using Serilog;
using Serilog.Events;
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace Bdeploy.Launcher
{

    /// <summary>
    /// Base class for all applications that are starting the Java LauncherCli
    /// </summary>
    public abstract class BaseLauncher
    {

        // The main class of the launcher to execute
        public static readonly string MAIN_CLASS = "io.bdeploy.launcher.cli.LauncherCli";

        /// The full path to the directory of the launcher executable
        public static readonly string LAUNCHER = Utils.GetExecutableDir();

        /// The full path to the BDeploy home directory
        public static readonly string HOME = Directory.GetParent(LAUNCHER).FullName;

        // The full path to the embedded JRE executable to use
        public static readonly string JRE = Path.Combine(LAUNCHER, "jre", "bin", "javaw.exe");

        // The full path to an optional property files containing arguments for the JVM
        public static readonly string JVM_PROPERTIES = Path.Combine(LAUNCHER, "bin", "launcher.properties");

        // The full path to the directory containing the JAR files
        public static readonly string LIB = Path.Combine(LAUNCHER, "lib");

        /// <summary>
        /// Event that is raised when launching the application fails.
        /// </summary>
        public event EventHandler<MessageEventArgs> LaunchFailed;

        /// <summary>
        /// Starts the process using the given arguments and waits for termination.
        /// </summary>
        /// <param name="arguments">Arguments to pass to the process.</param>
        /// <returns></returns>
        protected int StartLauncher(string arguments)
        {
            if (Log.IsEnabled(LogEventLevel.Debug))
            {
                Log.Debug("Starting launcher with arguments: {0}", arguments);
            }

            // Create special logger that writes to a separate file
            string path = Path.Combine(LogFactory.GetLogsDir(), GetAppLoggerName());
            ILogger appLogger = LogFactory.GetAppLogger(path);

            // Startup minion and wait for termination
            try
            {
                using (Process process = new Process())
                {
                    process.StartInfo.FileName = JRE;
                    process.StartInfo.CreateNoWindow = true;
                    process.StartInfo.UseShellExecute = false;
                    process.StartInfo.Arguments = arguments;
                    process.StartInfo.WorkingDirectory = LAUNCHER;
                    process.StartInfo.ErrorDialog = false;
                    process.StartInfo.RedirectStandardOutput = true;
                    process.StartInfo.RedirectStandardError = true;
                    process.OutputDataReceived += (sender, e) => Process_OutputDataReceived(appLogger, e);
                    process.ErrorDataReceived += (sender, e) => Process_ErrorDataReceived(appLogger, e);
                    process.Start();

                    // Write log file about the startup
                    Log.Information("Launcher started. PID = {0}", process.Id);
                    Log.Information("Output of launcher is written to a separate log file.");
                    Log.Information("See {0} for more details.", GetAppLoggerName());

                    // Wait until the process terminates
                    process.BeginOutputReadLine();
                    process.BeginErrorReadLine();
                    process.WaitForExit();
                    int exitCode = process.ExitCode;
                    Log.Information("Launcher terminated. ExitCode = {0}", exitCode);
                    return exitCode;
                }
            }
            catch (Win32Exception e)
            {
                LaunchFailed?.Invoke(this, new MessageEventArgs(e.ToString()));
                Log.Error("Failed to start launcher.", e);
                return -1;
            }
        }

        /// <summary>
        /// Callback method that logs the message that is written to the process error stream
        /// </summary>
        private void Process_ErrorDataReceived(ILogger logger, DataReceivedEventArgs e)
        {
            logger.Error(e.Data);
        }

        /// <summary>
        /// Callback method that logs the message that is written to the process output stream
        /// </summary>
        private void Process_OutputDataReceived(ILogger logger, DataReceivedEventArgs e)
        {
            logger.Information(e.Data);
        }

        /// <summary>
        /// Returns the name of the log file where all logs of the launched applications are written to.
        /// </summary>
        /// <returns></returns>
        protected abstract string GetAppLoggerName();

        /// <summary>
        /// Appends custom JVM arguments defined in the properties file
        /// </summary>
        /// <param name="builder"></param>
        protected void AppendCustomJvmArguments(StringBuilder builder)
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
