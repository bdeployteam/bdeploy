using System.Windows;
using System.Windows.Controls;

namespace Bdeploy.Installer.Views
{
    /// <summary>
    /// Interaction logic for LaunchView.xaml
    /// </summary>
    public partial class LaunchView : UserControl {

        private readonly Window window;

        public LaunchView(Window window) {
            InitializeComponent();
            this.window = window;
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e) {
            window.Close();
        }
    }
}
