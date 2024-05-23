using System.Windows;
using System.Windows.Input;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Interaction logic for WaitingWindow.xaml
    /// </summary>
    public partial class WaitingWindow : Window {
        private readonly CancelEventArgs eventArgs;

        public WaitingWindow(CancelEventArgs eventArgs) {
            this.eventArgs = eventArgs;
            InitializeComponent();
        }

        private void CancelButton_Click(object sender, RoutedEventArgs e) {
            eventArgs.Result = true;
            Close();
        }

        private void Window_MinimizeButton_Click(object sender, RoutedEventArgs e) {
            WindowState = WindowState.Minimized;
        }

        private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e) {
            DragMove();
        }
    }
}
