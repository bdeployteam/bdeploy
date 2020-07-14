using Bdeploy.Shared;
using Microsoft.Win32;
using System;
using System.IO;
using System.Windows;

namespace Bdeploy.FileAssoc {
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window {
        public MainWindow() {
            InitializeComponent();
            InitDefaults();
        }

        private void InitDefaults() {
            LauncherPath.Text = Path.Combine(Utils.GetWorkingDir(), "BDeploy.exe");
        }

        private void BrowseLauncher_Click(object sender, RoutedEventArgs e) {
            //Let the user chosse the launcher
            OpenFileDialog dlg = new OpenFileDialog {
                DefaultExt = ".exe",
                Filter = "BDeploy Launcher (*.exe)|*.exe"
            };

            // Display OpenFileDialog by calling ShowDialog method 
            Nullable<bool> result = dlg.ShowDialog();
            if (result != true) {
                return;
            }

            LauncherPath.Text = dlg.FileName;
        }

        private void CreateAssociation_Click(object sender, RoutedEventArgs e) {
            string path = LauncherPath.Text.ToLower();
            FileAssociation.CreateAssociation(path);
        }

        private void DeleteAssociation_Click(object sender, RoutedEventArgs e) {
            FileAssociation.RemoveAssociation();
        }

        private void CreateAssociationAsAdmin_Click(object sender, RoutedEventArgs e) {
            if (Utils.IsAdmin()) {
                string path = LauncherPath.Text.ToLower();
                FileAssociation.CreateAssociationForAllUsers(path);
            } else {
                string argument = "/CreateForAllUsers \"" + LauncherPath.Text.ToLower() + "\"";
                Launcher.RunAsAdmin(argument);
            }
        }

        private void DeleteAssociationAsAdmin_Click(object sender, RoutedEventArgs e) {
            // Remove for this user
            FileAssociation.RemoveAssociation();

            // Remove for all others
            if (Utils.IsAdmin()) {
                FileAssociation.RemoveAssociationForAllUsers();
            } else {
                string argument = "/RemoveForAllUsers";
                Launcher.RunAsAdmin(argument);
            }
        }

        private void LauncherPath_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e) {
            string filePath = LauncherPath.Text.ToLower();
            bool fileExists = File.Exists(filePath);

            CreateAssociation.IsEnabled = fileExists;
            CreateAssociationAsAdmin.IsEnabled = fileExists;
        }
    }
}
