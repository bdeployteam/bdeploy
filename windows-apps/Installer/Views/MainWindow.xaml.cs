using Bdeploy.Installer.Views;
using System;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;

namespace Bdeploy.Installer
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window {
        private readonly AppInstaller installer;
        private readonly InstallNowView installNowView;
        private readonly ProgressView progressView;
        private readonly ErrorView errorView;
        private readonly LaunchView launchView;

        public MainWindow(AppInstaller installer) {
            InitializeComponent();
            this.installer = installer;

            // Create all views
            installNowView = new InstallNowView(this, installer);
            progressView = new ProgressView(installer);
            errorView = new ErrorView(this);
            launchView = new LaunchView(this);

            // Show progres page after start
            this.installer.Begin += Installer_Begin;

            // Show error page on error
            this.installer.Error += Installer_Error;

            // Display launcher page when finished
            this.installer.Success += Installer_Success;

            // Show error page or install now page
            if (!installer.IsConfigValid()) {
                errorView.SetMessage("Configuration is invalid or corrupt");
                WindowContent.Content = errorView;
            } else {
                WindowContent.Content = installNowView;
            }
        }

        private void Installer_Begin(object sender, EventArgs e) {
            Dispatcher.Invoke(() => {
                WindowContent.Content = progressView;
            });
        }

        private void Installer_Success(object sender, EventArgs e) {
            Dispatcher.Invoke(() => {
                // If we installed an application then we automically close the window
                // If we just installed the launcher we show the success view.
                if (installer.config.CanInstallApp()) {
                    installer.Launch(new string[0], false);
                    Close();
                } else {
                    WindowContent.Content = launchView;
                }
            });
        }

        private void Installer_Error(object sender, MessageEventArgs e) {
            Dispatcher.Invoke(() => {
                errorView.SetMessage(e.Message);
                WindowContent.Content = errorView;
            });
        }

        private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e) {
            DragMove();
        }

        private void Window_MinimizeButton_Click(object sender, RoutedEventArgs e) {
            WindowState = WindowState.Minimized;
        }

        private void Window_CloseButton_Click(object sender, RoutedEventArgs e) {
            installer.Canceled = true;

            // Application cannot be closed while we do some task
            if (WindowContent != progressView) {
                Close();
            }
        }

    }

}
