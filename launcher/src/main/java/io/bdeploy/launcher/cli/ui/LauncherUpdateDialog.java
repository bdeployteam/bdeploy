package io.bdeploy.launcher.cli.ui;

import io.bdeploy.launcher.cli.SoftwareUpdateException;

/**
 * A dialog showing that a required software update is available but cannot be installed.
 */
public class LauncherUpdateDialog extends LauncherDialog {

    private static final long serialVersionUID = 1L;

    public static void main(String[] args) {
        LauncherUpdateDialog dialog = new LauncherUpdateDialog();
        dialog.showUpdateRequired(new SoftwareUpdateException("myApp", "Installed=1.2.0 Available=1.3.0"));
    }

    /**
     * Creates a new dialog to show an error message to the user
     */
    public LauncherUpdateDialog() {
        setTitle("Software Update");
    }

    /**
     * Opens the dialog to show that a required update is available but cannot be installed.
     */
    public void showUpdateRequired(SoftwareUpdateException ex) {
        setVisible(true);

        // Default icon and text in header
        headerIcon.setIcon(loadIcon("/update.png", 32, 32));
        headerText.setText("Software Update Required");

        // Default error summary
        errorSummary.setText("<html>A required software update is available " +  //
                "but cannot be installed due to insufficient permissions. " + //
                "Contact the system administrator.</html>");

        // Detailed error message including version
        errorDetails.setText(getDetailedErrorMessage(ex));

        // Block until dialog is closed
        waitForExit();
    }

}
