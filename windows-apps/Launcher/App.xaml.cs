using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using Serilog;
using System;
using System.IO;
using System.Threading.Tasks;
using System.Windows;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application {
        /// Exit code of the launcher signaling that an update is available
        public static readonly int EX_UPDATE = 42;

        // Window shown while we are waiting 
        private WaitingWindow waitingWindow;

        /// <summary>
        /// Callback method that is called on application startup
        /// </summary>
        async void App_Startup(object sender, StartupEventArgs e) {
            // Set the shutdown mode. Otherwise we won't be able to 
            // Show the same window multiple times in our callback
            this.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            // Initialize logging infrastructure
            string path = Path.Combine(PathProvider.GetLogsDir(), "launcher-log.txt");
            Log.Logger = LogFactory.CreateGlobalLogger(path);

            // Launch or Uninstall
            int exitCode;
            if (Utils.HasArgument(e.Args, "/Uninstall")) {
                exitCode = DoUninstall(e.Args);
            } else {
                exitCode = await DoLaunch(e.Args);
            }
            Current.Shutdown(exitCode);
        }

        /// <summary>
        /// Launches the application using the given arguments
        /// </summary>
        /// <param name="args"></param>
        private async Task<int> DoLaunch(string[] args) {
            // Bug in Firefox: When using Click & Start then Firefox does not quote the arguments correctly.
            // Thus an application with a space in the name "My App.bdeploy" is passed as two individual arguments.
            // instead of a single one. As a workaround we combine all arguments into a single one
            string clickAndStartFile = string.Join(" ", args);

            // Launch and wait for termination
            AppLauncher launcher = new AppLauncher(clickAndStartFile);
            launcher.UpdateFailed += Launcher_UpdateFailed;
            launcher.UpdateWaiting += Launcher_UpdateWaiting;
            launcher.StartUpdating += Launcher_StartUpdating;
            int exitCode = launcher.Start();

            // Check if another launcher has launched us
            // If so then exit code handling is done by the other launcher
            bool isDelegate = Environment.GetEnvironmentVariable("BDEPLOY_DELEGATE") != null;
            if (exitCode != EX_UPDATE || isDelegate) {
                return exitCode;
            }

            // Apply updates
            bool success = await Task.Run(() => launcher.ApplyUpdates());
            if (!success) {
                return -1;
            }

            // Startup launcher with original arguments
            if (!launcher.Restart()) {
                return -1;
            }
            return 0;
        }

        /// <summary>
        /// Uninstalls the application using the given arguments
        /// </summary>
        private int DoUninstall(string[] args) {
            string clickAndStartFile = FindClickAndStart(args);
            if (clickAndStartFile == null) {
                Console.WriteLine("Unexpected number of arguments. Usage BDeploy.exe /Uninstall myApp.bdeploy");
                return -1;
            }
            AppUninstaller uninstaller = new AppUninstaller(clickAndStartFile);
            return uninstaller.Start();
        }

        /// <summary>
        /// Evaluates the arguments to find the "/Uninstall <file>" pair.
        /// </summary>
        /// <param name="args">Arguments pass to the application</param>
        /// <returns>File argument specified after the /Uninstall option</returns>
        private static string FindClickAndStart(string[] args) {
            for (int i = 0; i < args.Length; i++) {
                String arg = args[i];
                if (!arg.Equals("/Uninstall", StringComparison.OrdinalIgnoreCase)) {
                    continue;
                }
                // Next argument must be the clickAndStartFile file
                if (i < args.Length - 1) {
                    return args[i + 1];
                }
            }
            return null;
        }

        /// <summary>
        /// Event raised by the launcher when the update is starting
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void Launcher_StartUpdating(object sender, object e) {
            Dispatcher.Invoke(() => {
                if (waitingWindow != null) {
                    waitingWindow.Close();
                    waitingWindow = null;
                }
            });
        }

        /// <summary>
        /// Event raised by the launcher in case that another operation is in progress.
        /// </summary>
        private void Launcher_UpdateWaiting(object sender, CancelEventArgs e) {
            Dispatcher.Invoke(() => {
                waitingWindow = new WaitingWindow(e);
                waitingWindow.Show();
            });
        }

        /// <summary>
        /// Event raised by the launcher in case that the update cannot be applied.
        /// </summary>
        private void Launcher_UpdateFailed(object sender, RetryCancelEventArgs e) {
            Dispatcher.Invoke(() => {
                ErrorWindow window = new ErrorWindow(e);
                window.ShowDialog();
            });
        }
    }
}
