using Microsoft.Win32;
using System;
using System.Collections;
using System.Text;
using System.Windows;
using System.Windows.Controls;

namespace Bdeploy.Installer.Views {
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
            ErrorDetails.Text = GetDetailedErrorMessage(message);
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

        private string GetDetailedErrorMessage(string message) {
            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("*** Date: {0}", DateTime.Now.ToString("dd.MM.yyyy hh:mm:ss"));
            builder.AppendLine().AppendLine();

            builder.AppendFormat("*** Error:").AppendLine();
            builder.Append(message);
            builder.AppendLine().AppendLine();

            builder.AppendFormat("*** Application:").AppendLine();
            builder.Append(Environment.CommandLine);
            builder.AppendLine().AppendLine();

            builder.Append("*** System environment variables: ").AppendLine();
            foreach (DictionaryEntry entry in Environment.GetEnvironmentVariables()) {
                builder.AppendFormat("{0}={1}", entry.Key, entry.Value).AppendLine();
            }
            builder.AppendLine();

            builder.Append("*** Operating system: ").AppendLine();
            builder.Append(ReadValueName("ProductName")).Append(Environment.NewLine);
            builder.AppendFormat("Version {0} (OS Build {1}.{2})", ReadValueName("ReleaseId"), ReadValueName("CurrentBuildNumber"), ReadValueName("UBR"));
            builder.AppendLine();

            return builder.ToString();
        }

        private string ReadValueName(String valueName) {
            return Registry.GetValue(@"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion", valueName, "").ToString();
        }


    }
}
