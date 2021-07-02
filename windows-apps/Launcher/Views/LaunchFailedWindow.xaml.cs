using Bdeploy.Shared;
using System.Windows;

namespace Bdeploy.Launcher.Views {
    /// <summary>
    /// Interaction logic for LaunchFailedWindow.xaml
    /// </summary>
    public partial class LaunchFailedWindow : Window {

        private bool detailsVisible = false;

        public LaunchFailedWindow(MessageEventArgs e) {
            InitializeComponent();
            ErrorDetails.Text = Utils.GetDetailedErrorMessage(e.Message);
        }

        private void DetailsButton_Click(object sender, RoutedEventArgs e) {
            if (detailsVisible) {
                ErrorMessage.Visibility = Visibility.Visible;
                ErrorDetails.Visibility = Visibility.Hidden;
                ErrorDetailsButton.Content = "Show Details";
                Height = 300;
            } else {
                ErrorMessage.Visibility = Visibility.Hidden;
                ErrorDetails.Visibility = Visibility.Visible;
                ErrorDetailsButton.Content = "Hide Details";
                Height = 450;
            }
            detailsVisible = !detailsVisible;
        }

        private void ClipboardButton_Click(object sender, RoutedEventArgs e) {
            Clipboard.SetText(ErrorDetails.Text);
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e) {
            Close();
        }
    }
}
