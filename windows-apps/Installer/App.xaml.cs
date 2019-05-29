using Bdeploy.Common;
using System.Threading.Tasks;
using System.Windows;

namespace Bdeploy
{
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application
    {
        private readonly Installer Installer = new Installer();

        async void App_Startup(object sender, StartupEventArgs e)
        {

            // Just launch if everything is installed
            int setupCode = await InstallIfMissing();
            if (setupCode != 0)
            {
                Current.Shutdown(setupCode);
                return;
            }

            // Launch application
            int appExitCode = Installer.Launch();
            Current.Shutdown(appExitCode);
        }

        /// <summary>
        /// Executes the installer if the application is not yet installed
        /// </summary>
        /// <returns></returns>
        private async Task<int> InstallIfMissing()
        {
            if (Installer.IsApplicationAlreadyInstalled())
            {
                return 0;
            }

            // Show progress during installation
            MainWindow mainWindow = new MainWindow(Installer);
            mainWindow.Show();

            // Execute installer and wait for success
            int returnCode = await Task.Run(() => Installer.Setup());
            if (returnCode == 0)
            {
                mainWindow.Close();
            }
            return returnCode;
        }


    }

}
