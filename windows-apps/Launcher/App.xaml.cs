using Bdeploy.Launcher.Src;
using Bdeploy.Launcher.Views;
using Bdeploy.Shared;
using Serilog;
using System;
using System.IO;
using System.Linq;
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
            ShutdownMode = ShutdownMode.OnExplicitShutdown;

            // Initialize logging infrastructure
            string path = Path.Combine(LogFactory.GetLogsDir(), "launcher-log.txt");
            Log.Logger = LogFactory.CreateGlobalLogger(path);
            Log.Information("Launcher started. Arguments {0} ", e.Args);

            // Launch, Uninstall or Browse
            int exitCode;
            if (e.Args.Length == 0) {
                exitCode = DoLaunchBrowser();
            } else if (Utils.HasArgument(e.Args, "/Uninstall")) {
                bool forAllUsers = Utils.HasArgument(e.Args, "/ForAllUsers");
                exitCode = DoUninstall(e.Args, forAllUsers);
            } else if (Utils.HasArgument(e.Args, "/Autostart")) {
                exitCode = DoHandleAutostart();
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
            string clickAndStartFile = FindClickAndStart(args);
            if (clickAndStartFile == null) {
                Log.Fatal("Unexpected number of arguments. Usage BDeploy.exe myApp.bdeploy");
                return -1;
            }

            // Remove click and start from arguments
            // Pass all others to the application
            args = args.Where(val => val != clickAndStartFile).ToArray();

            // Launch and wait for termination
            AppLauncher launcher = new AppLauncher(clickAndStartFile);
            launcher.UpdateFailed += Launcher_UpdateFailed;
            launcher.UpdateWaiting += Launcher_UpdateWaiting;
            launcher.StartUpdating += Launcher_StartUpdating;
            launcher.LaunchFailed += Launcher_LaunchFailed;
            int exitCode = launcher.Start(args);

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
            if (!launcher.Restart(args)) {
                return -1;
            }
            return 0;
        }

        /// <summary>
        /// Launches all applications which are configured for autostart
        /// </summary>
        private int DoHandleAutostart() {
            AppAutostarter appBrowser = new AppAutostarter();
            return appBrowser.Start();
        }

        /// <summary>
        /// Uninstalls the application using the given arguments
        /// </summary>
        private int DoUninstall(string[] args, bool forAllUsers) {
            string clickAndStartFile = FindClickAndStart(args);
            if (clickAndStartFile == null) {
                Log.Fatal("Unexpected number of arguments. Usage BDeploy.exe /Uninstall myApp.bdeploy");
                return -1;
            }
            AppUninstaller uninstaller = new AppUninstaller(clickAndStartFile, forAllUsers);
            return uninstaller.Start();
        }

        /// <summary>
        /// Launches the browser to view all installed apps
        /// </summary>
        private int DoLaunchBrowser() {
            AppBrowser appBrowser = new AppBrowser();
            return appBrowser.Start();
        }

        /// <summary>
        /// Evaluates the arguments to find the "Click&Start" file. 
        /// NOTE: We cannot rely on the file extension as Firefox will pass a .bdeploy.json file.
        /// </summary>
        /// <param name="args">Arguments pass to the application</param>
        /// <returns>The argument refering to the "Click&Start" file.</returns>
        private static string FindClickAndStart(string[] args) {
            foreach (string arg in args) {
                ClickAndStartDescriptor descriptor = ClickAndStartDescriptor.FromFile(arg);
                if (descriptor != null) {
                    return arg;
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
                UpdateFailedWindow window = new UpdateFailedWindow(e);
                window.ShowDialog();
            });
        }

        /// <summary>
        /// Event raised by the launcher in case that launching the Java JVM fails.
        /// </summary>
        private void Launcher_LaunchFailed(object sender, MessageEventArgs e) {
            Dispatcher.Invoke(() => {
                LaunchFailedWindow window = new LaunchFailedWindow(e);
                window.ShowDialog();
            });
        }
    }
}
