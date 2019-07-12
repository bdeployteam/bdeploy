using Microsoft.Win32;
using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace Bdeploy.Installer
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        private readonly AppInstaller Installer;
        private bool detailsVisible = false;

        public MainWindow(AppInstaller installer)
        {
            InitializeComponent();
            Installer = installer;

            // Display progress
            Installer.NewSubtask += Installer_NewSubtask;
            Installer.Worked += Installer_Worked;

            // Show error page on error
            Installer.Error += Installer_Error;

            // Load icon when available
            Installer.IconLoaded += Installer_IconLoaded;

            // Ensure progress page is visible
            ProgressGrid.Visibility = Visibility.Visible;
            ErrorGrid.Visibility = Visibility.Hidden;
            ErrorMessage.Visibility = Visibility.Visible;
            ErrorDetails.Visibility = Visibility.Hidden;
        }

        private void Installer_IconLoaded(object sender, IconEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                ApplicationIcon.Source = BitmapFrame.Create(new System.Uri(e.Icon));
            });
        }

        private void Installer_Error(object sender, MessageEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                ProgressGrid.Visibility = Visibility.Hidden;
                ErrorGrid.Visibility = Visibility.Visible;
                ErrorDetails.Text = GetDetailedErrorMessage(e.Message);
            });
        }

        private void Installer_NewSubtask(object sender, SubTaskEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                ProgressBar.IsIndeterminate = e.TotalWork == -1;
                ProgressBar.Value = 0;
                ProgressBar.Minimum = 0;
                ProgressBar.Maximum = e.TotalWork;
                ProgressText.Text = e.TaskName;
            });
        }

        private void Installer_Worked(object sender, WorkedEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                ProgressBar.Value += e.Worked;
            });
        }

        private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            DragMove();
        }

        private void Window_MinimizeButton_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void Window_CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Installer.Canceled = true;
            if (ErrorGrid.Visibility == Visibility.Visible)
            {
                Close();
            }
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void DetailsButton_Click(object sender, RoutedEventArgs e)
        {
            if (detailsVisible)
            {
                ErrorMessage.Visibility = Visibility.Visible;
                ErrorDetails.Visibility = Visibility.Hidden;
                DetailsButton.Content = "Show Details";
                Height = 300;
            }
            else
            {
                ErrorMessage.Visibility = Visibility.Hidden;
                ErrorDetails.Visibility = Visibility.Visible;
                DetailsButton.Content = "Hide Details";
                Height = 450;
            }
            detailsVisible = !detailsVisible;
        }

        private void ClipboardButton_Click(object sender, RoutedEventArgs e)
        {
            Clipboard.SetText(ErrorDetails.Text);
        }

        private string GetDetailedErrorMessage(string message)
        {
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
            foreach (DictionaryEntry entry in Environment.GetEnvironmentVariables())
            {
                builder.AppendFormat("{0}={1}", entry.Key, entry.Value).AppendLine();
            }
            builder.AppendLine();

            builder.Append("*** Operating system: ").AppendLine();
            builder.Append(ReadValueName("ProductName")).Append(Environment.NewLine);
            builder.AppendFormat("Version {0} (OS Build {1}.{2})", ReadValueName("ReleaseId"), ReadValueName("CurrentBuildNumber"), ReadValueName("UBR"));
            builder.AppendLine();

            return builder.ToString();
        }

        private string ReadValueName(String valueName)
        {
            return Registry.GetValue(@"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion", valueName, "").ToString();
        }
    }

}
