using System;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

namespace Bdeploy.Installer.Views {
    /// <summary>
    /// Interaction logic for ProgressView.xaml
    /// </summary>
    public partial class ProgressView : UserControl {
        public ProgressView(AppInstaller installer) {
            InitializeComponent();

            // Show metadata when available
            installer.IconLoaded += Installer_IconLoaded;
            installer.AppInfo += Installer_AppInfo;

            // Display progress
            installer.NewSubtask += Installer_NewSubtask;
            installer.Worked += Installer_Worked;
        }

        
        private void Installer_AppInfo(object sender, AppInfoEventArgs e) {
            Dispatcher.Invoke(() => {
                ApplicationName.Text = e.AppName ?? "";
                ApplicationVendor.Text = e.VendorName ?? "";
            });
        }

        private void Installer_IconLoaded(object sender, IconEventArgs e) {
            Dispatcher.Invoke(() => {
                ApplicationIcon.Source = BitmapFrame.Create(new System.Uri(e.Icon));
            });
        }

        private void Installer_NewSubtask(object sender, SubTaskEventArgs e) {
            Dispatcher.Invoke(() => {
                ProgressBar.IsIndeterminate = e.TotalWork == -1;
                ProgressBar.Value = 0;
                ProgressBar.Minimum = 0;
                ProgressBar.Maximum = e.TotalWork;
                ProgressText.Text = e.TaskName;
            });
        }

        private void Installer_Worked(object sender, WorkedEventArgs e) {
            Dispatcher.Invoke(() => {
                ProgressBar.Value += e.Worked;
            });
        }

    }
}
