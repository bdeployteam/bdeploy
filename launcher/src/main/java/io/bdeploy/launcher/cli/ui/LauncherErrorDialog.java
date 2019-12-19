package io.bdeploy.launcher.cli.ui;

/**
 * A dialog showing the exception that occured during launching.
 */
public class LauncherErrorDialog extends LauncherDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Shows the error dialog with a dummy stack-trace. Only useful for testing.
     */
    public static void main(String[] args) {
        LauncherErrorDialog dialog = new LauncherErrorDialog();
        dialog.showError(new RuntimeException());
    }

    /**
     * Creates a new dialog to show an error message to the user
     */
    public LauncherErrorDialog() {
        setTitle("Error");
    }

    /**
     * Opens the dialog to show the error message to the user. Blocks until the user closes the dialog.
     *
     * @param ex the exception that occurred
     */
    public void showError(Throwable ex) {
        setVisible(true);

        // Default icon and text in header
        headerIcon.setIcon(loadIcon("/error.png", 32, 32));
        headerText.setText("Application could not be launched");

        // Default error summary
        errorSummary.setText("<html>Unexpected error occurred while launching the application. " + //
                "If the problem persists, contact the system administrator.</html>");

        // Detailed error message including stacktrace
        errorDetails.setText(getDetailedErrorMessage(ex));

        // Block until dialog is closed
        waitForExit();
    }

}
