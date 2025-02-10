using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using System.Collections;
using System.Collections.Generic;
using System.Net;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Documents;

namespace Bdeploy.Installer {
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application {
        private async void App_Startup(object sender, StartupEventArgs e) {
            // Enforce usage of TLS 1.2
            // We target .NET 4.5 and therefore we need to explicitly change the default
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

            // Whether or not to perform an unattended installation
            bool unattended = Utils.HasArgument(e.Args, "/Unattended");
            bool forAllUsers = Utils.HasArgument(e.Args, "/ForAllUsers");
            bool noSystemChanges = Utils.HasArgument(e.Args, "/NoSystemChanges");

            // Read configuration and create installer
            Config config = ConfigStorage.GetConfig(e);
            AppInstaller installer = new AppInstaller(config, forAllUsers, noSystemChanges);


            // Download and install application without user interaction
            if (unattended) {
                int setupCode = await Task.Run(() => installer.SetupAsync());
                if (setupCode == 0 && config.CanInstallApp()) {
                    List<string> args = new List<string> { };
                    if (noSystemChanges) {
                        args.Add("--noSystemChanges");
                    }
                    args.Add("--updateOnly");
                    args.Add("--unattended");
                    setupCode = installer.Launch(args.ToArray(), true);
                }
                Current.Shutdown(setupCode);
                return;
            }

            // Open the installer window
            MainWindow mainWindow = new MainWindow(installer, noSystemChanges);
            mainWindow.Show();
        }
    }
}
