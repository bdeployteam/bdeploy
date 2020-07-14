using Bdeploy.Shared;
using Serilog;
using Serilog.Events;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace Bdeploy.Launcher.Models {

    /// <summary>
    /// Base class for all applications that are starting the Java LauncherCli
    /// </summary>
    public abstract class BaseLauncher {

        // The main class of the launcher to execute
        public static readonly string MAIN_CLASS = "io.bdeploy.launcher.cli.LauncherCli";

        /// The full path to the current working directory of the launcher
        public static readonly string LAUNCHER = Utils.GetWorkingDir();

        // The full path to the embedded JRE executable to use
        public static readonly string JRE = Path.Combine(LAUNCHER, "jre", "bin", "javaw.exe");

        // The full path to an optional property files containing arguments for the JVM
        public static readonly string JVM_PROPERTIES = Path.Combine(LAUNCHER, "bin", "launcher.properties");

        // The full path to the directory containing the JAR files
        public static readonly string LIB = Path.Combine(LAUNCHER, "lib");

        // The full path of the .bdeploy file to launch
        protected readonly string clickAndStartFile;

        // The deserialized clickAndStartFile
        protected ClickAndStartDescriptor descriptor;

        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="clickAndStartFile">The .bdeploy file</param>
        protected BaseLauncher(string clickAndStartFile) {
            this.clickAndStartFile = clickAndStartFile;
        }

        /// <summary>
        /// Checks that the embedded JRE is working.
        /// </summary>
        public bool ValidateEmbeddedJre() {
            // JRE must exist 
            if (!File.Exists(JRE)) {
                Log.Fatal("Embedded JRE '{0}' not found.", JRE);
                Log.Information("Exiting application.");
                return false;
            }

            // Try to launch it
            int returnCode = Utils.RunProcessAndWait(JRE, "-version");
            if (returnCode != 0) {
                Log.Fatal("Embedded JRE terminated with exit code {0}", returnCode);
                Log.Fatal("JRE located in {0} is corrupt or missing", JRE);
                Log.Information("Exiting application.");
                return false;
            }
            return true;
        }

        /// <summary>
        /// Checks that the given file point to a valid application descriptor
        /// </summary>
        protected bool ValidateDescriptor() {
            // File must exist 
            if (!File.Exists(clickAndStartFile)) {
                Log.Fatal("Application descriptor '{0}' not found.", clickAndStartFile);
                Log.Information("Exiting application.");
                return false;
            }

            // We must be able to deserialize the content
            descriptor = ClickAndStartDescriptor.FromFile(clickAndStartFile);
            if (descriptor == null) {
                Log.Fatal("Cannot deserialize application descriptor.", clickAndStartFile);
                Log.Information("Exiting application.");
                return false;
            }
            return true;
        }

        /// <summary>
        /// Starts the process using the given arguments and waits for termination.
        /// </summary>
        /// <param name="arguments">Arguments to pass to the process.</param>
        /// <returns></returns>
        protected int StartLauncher(string arguments) {
            if (Log.IsEnabled(LogEventLevel.Debug)) {
                Log.Debug("Starting launcher with arguments: {0}", arguments);
            }

            // Create special logger that writes to a separate file
            string path = Path.Combine(PathProvider.GetLogsDir(), GetAppLoggerName());
            ILogger appLogger = LogFactory.GetAppLogger(path);

            // Startup minion and wait for termination
            using (Process process = new Process()) {
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

        /// <summary>
        /// Callback method that logs the message that is written to the process error stream
        /// </summary>
        private void Process_ErrorDataReceived(ILogger logger, DataReceivedEventArgs e) {
            logger.Error(e.Data);
        }

        /// <summary>
        /// Callback method that logs the message that is written to the process output stream
        /// </summary>
        private void Process_OutputDataReceived(ILogger logger, DataReceivedEventArgs e) {
            logger.Information(e.Data);
        }

        /// <summary>
        /// Returns the name of the log file used by the launcher.
        /// </summary>
        /// <returns></returns>
        private string GetAppLoggerName() {
            return descriptor.ApplicationId + "-log.txt";
        }

        /// <summary>
        /// Appends custom JVM arguments defined in the properties file
        /// </summary>
        /// <param name="builder"></param>
        protected void AppendCustomJvmArguments(StringBuilder builder) {
            bool hasXmX = false;
            if (File.Exists(JVM_PROPERTIES)) {
                foreach (string line in File.ReadAllLines(JVM_PROPERTIES, UTF8Encoding.UTF8)) {
                    // Ignore comments
                    string arg = line.Trim();
                    if (arg.StartsWith("#")) {
                        continue;
                    }

                    // Check if we have a max memory setting
                    if (arg.StartsWith("-Xmx")) {
                        hasXmX = true;
                    }

                    builder.Append(arg);
                    builder.Append(" ");
                }
            }

            // Ensure that a memory limit is configured
            if (!hasXmX) {
                builder.Append("-Xmx256m ");
            }
        }

    }
}
