using System.Windows;
using System.Windows.Input;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Interaction logic for ErrorWindow.xaml
    /// </summary>
    public partial class ErrorWindow : Window {
        private readonly RetryCancelEventArgs eventArgs;

        public ErrorWindow(RetryCancelEventArgs eventArgs) {
            this.eventArgs = eventArgs;
            InitializeComponent();
        }

        private void CancelButton_Click(object sender, RoutedEventArgs e) {
            eventArgs.Result = RetryCancelMode.CANCEL;
            Close();
        }

        private void RetryButton_Click(object sender, RoutedEventArgs e) {
            eventArgs.Result = RetryCancelMode.RETRY;
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
