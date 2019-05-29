using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using System;
using System.IO;
using System.Threading.Tasks;
using System.Windows;

namespace Bdeploy.Installer
{
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application
    {
        async void App_Startup(object sender, StartupEventArgs e)
        {
            // Just print config and exit application. 
            bool printAndExit = Utils.HasArgument(e.Args, "/ShowEmbeddedConfig");
            if (printAndExit)
            {
                Config embedded = ConfigStorage.ReadEmbeddedConfiguration();
                Console.WriteLine("Embedded configuration data: ");
                embedded.LogToConsole();
                Current.Shutdown(0);
                return;
            }

            // FIXME: Get token from embedded configuration file
            // Token contains the certificate. Pass this to the HttpClient
            Utils.AllowUntrustedCertificates();

            // Whether or not to perform an unattended installation
            bool unattended = Utils.HasArgument(e.Args, "/Unattended");

            // Read configuration and create installer
            Config config = ConfigStorage.GetConfig(e);
            AppInstaller installer = new AppInstaller(config);

            // Download and install application
            int setupCode = await InstallIfMissing(installer, unattended);
            if (setupCode != 0)
            {
                return;
            }

            // Installation done when in unattended mode
            if (unattended)
            {
                Current.Shutdown(setupCode);
                return;
            }

            // Launch application and terminate installer
            installer.Launch();
            Current.Shutdown(0);
        }

        private async Task<int> InstallIfMissing(AppInstaller installer, bool unattended)
        {
            // Show progress during installation
            MainWindow mainWindow = null;
            if (!unattended)
            {
                mainWindow = new MainWindow(installer);
                mainWindow.Show();
            }
            // Error and progress handling using a simple logger
            else
            {
                installer.Error += Installer_Error;
                installer.NewSubtask += Installer_NewSubtask;
            }

            // Execute installer and wait for success
            int returnCode = await Task.Run(() => installer.Setup());
            Console.WriteLine("Setup finished with return code: ${0}", returnCode);

            // Auto-Close window when installation was successfull
            if (returnCode == 0 && mainWindow != null)
            {
                mainWindow.Close();
            }
            return returnCode;
        }

        private void Installer_Error(object sender, MessageEventArgs e)
        {
            Console.WriteLine("Error occured during installation: ${0}", e.Message);
        }

        private void Installer_NewSubtask(object sender, SubTaskEventArgs e)
        {
            Console.WriteLine("New task: ${0}", e.TaskName);
        }
    }

    /// <summary>
    /// Utility tool in order to write the config.txt file that is embedded during the build.
    /// Should only be invoked from withing Visual Studio
    /// </summary>
    public sealed class Tool
    {
        public static int Main()
        {
            string basePath = Utils.GetWorkingDir();
            string configFile = Path.GetFullPath(Path.Combine(basePath, "..\\..\\Resources\\config.txt"));
            ConfigStorage.WriteConfiguration(configFile, new Config());
            Console.WriteLine("Updated configuration file template.");
            Console.WriteLine("File written to {0}", configFile);
            return 0;
        }
    }

}
