using System.IO;
using System.Text;

namespace Bdeploy.Launcher
{

    /// <summary>
    /// Launches the Browser tool to see all installed applications
    /// </summary>
    public class AppBrowser : BaseLauncher {

        /// <summary>
        /// Creates a new instance.
        /// </summary>
        public AppBrowser() {
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
            builder.AppendFormat("browser ");
            builder.AppendFormat("\"--homeDir={0}\" ", HOME);

            // Abort if uninstallation was not OK
            return StartLauncher(builder.ToString());
        }

        protected override string GetAppLoggerName() {
            return "browser-log.txt";
        }
    }
}