using Bdeploy.Shared;
using System.Windows;
using System.Windows.Controls;

namespace Bdeploy.Installer.Views
{
    /// <summary>
    /// Interaction logic for ErrorView.xaml
    /// </summary>
    public partial class ErrorView : UserControl {

        private readonly Window window;
        private bool detailsVisible = false;

        public ErrorView(Window window) {
            InitializeComponent();
            this.window = window;
        }

        internal void SetMessage(string message) {
            ErrorDetails.Text = Utils.GetDetailedErrorMessage(message);
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e) {
            window.Close();
        }

        private void DetailsButton_Click(object sender, RoutedEventArgs e) {
            if (detailsVisible) {
                ErrorMessage.Visibility = Visibility.Visible;
                ErrorDetails.Visibility = Visibility.Hidden;
                ErrorDetailsButton.Content = "Show Details";
                window.Height = 300;
            } else {
                ErrorMessage.Visibility = Visibility.Hidden;
                ErrorDetails.Visibility = Visibility.Visible;
                ErrorDetailsButton.Content = "Hide Details";
                window.Height = 450;
            }
            detailsVisible = !detailsVisible;
        }

        private void ClipboardButton_Click(object sender, RoutedEventArgs e) {
            Clipboard.SetText(ErrorDetails.Text);
        }

    }
}
