using Bdeploy.Shared;
using System;
using System.Windows;

namespace Bdeploy.FileAssoc
{
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application
    {
        void App_Startup(object sender, StartupEventArgs e)
        {
            if (e.Args.Length == 0)
            {
                MainWindow mainWindow = new MainWindow();
                mainWindow.Show();
            }
            else
            {
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
        private bool HandleArguments(string[] args)
        {
            string mainArg = args[0];
            if (mainArg == "/CreateForAllUsers" && args.Length == 2)
            {
                if (!Utils.IsAdmin())
                {
                    Console.WriteLine("Administrative privileges required to create association for all users");
                    return false;
                }
                string launcherPath = args[1];
                FileAssociation.CreateAssociation(launcherPath, true);
                return true;
            }
            if (mainArg == "/CreateForCurrentUser" && args.Length == 2)
            {
                string launcherPath = args[1];
                FileAssociation.CreateAssociation(launcherPath, false);
                return true;
            }
            if (mainArg == "/RemoveForAllUsers")
            {
                if (!Utils.IsAdmin())
                {
                    Console.WriteLine("Administrative privileges required to remove association for all users");
                    return false;
                }
                FileAssociation.RemoveAssociation(true);
                return true;
            }
            if (mainArg == "/RemoveForCurrentUser")
            {
                FileAssociation.RemoveAssociation(false);
                return true;
            }
            Console.WriteLine("Unsupported argument: Must be one of /CreateForCurrentUser <launcher> /CreateForAllUsers <launcher> /RemoveForAllUsers /RemoveForCurrentUser.");
            return false;
        }
    }
}
