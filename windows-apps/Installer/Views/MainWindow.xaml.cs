using System.IO;
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
                ErrorDetails.Text = e.Message;
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
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

    }

}
