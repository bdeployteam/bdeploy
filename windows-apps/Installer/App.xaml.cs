using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using System.Threading.Tasks;
using System.Windows;

namespace Bdeploy.Installer {
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application {
        async void App_Startup(object sender, StartupEventArgs e) {
            // Whether or not to perform an unattended installation
            bool unattended = Utils.HasArgument(e.Args, "/Unattended");
            bool forAllUsers = Utils.HasArgument(e.Args, "/ForAllUsers");

            // Read configuration and create installer
            Config config = ConfigStorage.GetConfig(e);
            AppInstaller installer = new AppInstaller(config, forAllUsers);

            // Download and install application without user interaction
            if (unattended) {
                int setupCode = await Task.Run(() => installer.SetupAsync());
                Current.Shutdown(setupCode);
                return;
            }

            // Open the installer window
            MainWindow mainWindow = new MainWindow(installer);
            mainWindow.Show();
        }

    }

}
