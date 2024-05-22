using System.IO;
using System.Text;

namespace Bdeploy.Launcher.Src {
    internal class AppAutostarter : BaseLauncher {
        /// <summary>
        /// Creates a new instance.
        /// </summary>
        public AppAutostarter() {
        }

        /// <summary>
        /// Starts the LauncherCli 
        /// </summary>
        public int Start() {
            // Build arguments to pass to the application
            StringBuilder builder = new StringBuilder();
            AppendCustomJvmArguments(builder);

            // Append classpath and mandatory arguments of application
            builder.AppendFormat("-cp \"{0}\" ", Path.Combine(LIB, "*"));
            builder.AppendFormat("{0} ", MAIN_CLASS);
            builder.AppendFormat("autostart ");
            builder.AppendFormat("\"--homeDir={0}\" ", HOME);

            // Start the launcher
            return StartLauncher(builder.ToString());
        }

        protected override string GetAppLoggerName() {
            return "autostart-log.txt";
        }
    }
}
