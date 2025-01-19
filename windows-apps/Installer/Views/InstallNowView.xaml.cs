using Bdeploy.Shared;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;

namespace Bdeploy.Installer.Views {
    /// <summary>
    /// Interaction logic for InstallNowView.xaml
    /// </summary>
    public partial class InstallNowView : UserControl {

        private readonly Window window;
        private readonly AppInstaller installer;

        public InstallNowView(Window window, AppInstaller installer, bool allowSystemChanges) {
            InitializeComponent();
            this.installer = installer;
            this.window = window;

            CreateShortcuts.IsChecked = allowSystemChanges;
            CreateShortcuts.IsEnabled = allowSystemChanges;
            NoSystemChangesInfoLabel.Visibility = allowSystemChanges ? Visibility.Hidden : Visibility.Visible;

            Config config = installer.config;
            if (installer.IsConfigValid()) {
                ServerUrl.Text = config.RemoteService.Uri;
                TargetDir.Text = installer.bdeployHome;

                ApplicationName.Text = config.ApplicationName ?? "BDeploy Click & Start Launcher";
                ApplicationVendor.Text = config.ProductVendor ?? "BDeploy Team";
            }
        }

        private async void InstallNowButton_Click(object sender, RoutedEventArgs e) {
            InstallNow.IsEnabled = false;
            await Task.Run(() => installer.SetupAsync());
            InstallNow.IsEnabled = true;
        }

        private void CloseHyperlink_Click(object sender, RoutedEventArgs e) {
            window.Close();
        }

        private void CreateShortcuts_Click(object sender, RoutedEventArgs e) {
            installer.createShortcuts = CreateShortcuts.IsChecked.GetValueOrDefault(true);
        }
    }
}
