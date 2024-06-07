using Bdeploy.Shared;
using System;
using System.Windows;

namespace Bdeploy.FileAssoc {

    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application {

        private void App_Startup(object sender, StartupEventArgs e) {
            if (e.Args.Length == 0) {
                MainWindow mainWindow = new MainWindow();
                mainWindow.Show();
            } else {
                bool success = HandleArguments(e.Args);
                int exitCode = success ? 0 : 1;
                Current.Shutdown(exitCode);
            }
        }

        /// <summary>
        /// Creates or removes the file association depending on the argumets
        /// </summary>
        /// <param name="args"></param>
        /// <returns>true if association was done and false otherwise</returns>
        private bool HandleArguments(string[] args) {
            string mainArg = args[0];
            if (mainArg == "/CreateForCurrentUser" && args.Length == 2) {
                return CreateForCurrentUser(args[1]);
            }
            if (mainArg == "/RemoveForCurrentUser") {
                return RemoveForCurrentUser();
            }
            if (mainArg == "/CreateForAllUsers" && args.Length == 2) {
                return CreateForAllUsers(args[1]);
            }
            if (mainArg == "/RemoveForAllUsers") {
                return RemoveForAllUsers();
            }
            if (mainArg == "/InstallApplication" && args.Length == 6) {
                return InstallApplication(args[1], args[2], args[3], args[4], args[5]);
            }
            if (mainArg == "/UninstallApplication" && args.Length == 3) {
                return UninstallApplication(args[1], args[2]);
            }
            Console.WriteLine("Unsupported argument: Must be one of the following:");
            Console.WriteLine("/CreateForCurrentUser <path\\to\\launcher.exe>");
            Console.WriteLine("/RemoveForCurrentUser");
            Console.WriteLine("/CreateForAllUsers <path\\to\\launcher.exe>");
            Console.WriteLine("/RemoveForAllUsers");
            Console.WriteLine("/InstallApplication <applicationId> <.fileExtension> <path\\to\\fileAssociationScript.bat> <path\\to\\icon.ico> <friendlyName>");
            Console.WriteLine("/UninstallApplication <applicationId> <.fileExtension>");
            return false;
        }

        private bool CreateForCurrentUser(string launcherPath) {
            Utils.LAUNCHER_FILE_ASSOC.CreateBDeployLauncherAssociaton(false, launcherPath);
            return true;
        }

        private bool RemoveForCurrentUser() {
            Utils.LAUNCHER_FILE_ASSOC.RemoveAssociation(false);
            return true;
        }

        private bool CreateForAllUsers(string launcherPath) {
            if (Utils.IsAdmin()) {
                Utils.LAUNCHER_FILE_ASSOC.CreateBDeployLauncherAssociaton(true, launcherPath);
                return true;
            }
            Console.WriteLine("Administrative privileges required to create association for all users");
            return false;
        }

        private bool RemoveForAllUsers() {
            if (Utils.IsAdmin()) {
                Utils.LAUNCHER_FILE_ASSOC.RemoveAssociation(true);
                return true;
            }
            Console.WriteLine("Administrative privileges required to remove association for all users");
            return false;
        }

        private bool InstallApplication(string applicationId, string fileExtension, string pathToScript, string pathToIcon, string friendlyName) {
            new FileAssociation(applicationId, fileExtension).CreateAssociation(false, pathToScript, pathToIcon, friendlyName);
            return true;
        }

        private bool UninstallApplication(string applicationId, string fileExtension) {
            new FileAssociation(applicationId, fileExtension).RemoveAssociation(false);
            return true;
        }
    }
}
