package io.bdeploy.launcher.cli.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.common.util.DateHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.ProcessHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.cli.LauncherTool;
import io.bdeploy.launcher.cli.SoftwareUpdateException;

/**
 * Provides static helpers to display a {@linkplain MessageDialog}
 */
public class MessageDialogs {

    private MessageDialogs() {
    }

    /**
     * Opens the dialog to show that a required update is available but cannot be installed.
     */
    public static void showUpdateRequired(ClickAndStartDescriptor config, SoftwareUpdateException ex) {
        MessageDialog dialog = new MessageDialog("Software Update");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/update.png", 32, 32));
        dialog.setHeaderText("Software Update Required");
        dialog.setSummary("<html>A required software update is available " +  //
                "but cannot be installed due to insufficient permissions. " + //
                "Contact the system administrator.</html>");
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
        dialog.waitForExit();
    }

    /**
     * Opens the dialog to show that launching the given application failed.
     */
    public static void showLaunchFailed(ClickAndStartDescriptor config, Throwable ex) {
        MessageDialog dialog = new MessageDialog("Error");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/error.png", 32, 32));
        dialog.setHeaderText("Application could not be launched");
        dialog.setSummary("<html>Unexpected error occurred while launching the application. " + //
                "If the problem persists, contact the system administrator.</html>");
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
        dialog.waitForExit();
    }

    /**
     * Opens the dialog to show uninstallation failed.
     */
    public static void showUninstallationFailed(ClickAndStartDescriptor config, Throwable ex) {
        MessageDialog dialog = new MessageDialog("Error");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/error.png", 32, 32));
        dialog.setHeaderText("Application could not be uninstalled");
        dialog.setSummary("<html>Unexpected error occurred while uninstalling the application. " + //
                "If the problem persists, contact the system administrator.</html>");
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
    }

    /** Returns the detailed error message to be displayed */
    private static String getDetailedErrorMessage(ClickAndStartDescriptor config, Throwable ex) {
        StringBuilder builder = new StringBuilder();

        builder.append("*** Date: ").append(DateHelper.format(new Date())).append("\n");
        builder.append("\n");

        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        builder.append("*** Stacktrace: \n").append(writer);
        builder.append("\n");

        builder.append("*** BDeploy properties: \n");
        builder.append("LauncherVersion=").append(VersionHelper.getVersion()).append("\n");
        if (config != null) {
            builder.append("ServerVersion=").append(getServerVersion(config)).append("\n");
            builder.append("ApplicationId=").append(config.applicationId).append("\n");
            builder.append("GroupId=").append(config.groupId).append("\n");
            builder.append("InstanceId=").append(config.instanceId).append("\n");
            builder.append("Host=").append(config.host.getUri()).append("\n");
            builder.append("Token=").append(config.host.getAuthPack()).append("\n");
        }
        builder.append("\n");

        builder.append("*** System properties: \n");
        Map<Object, Object> properties = new TreeMap<>(System.getProperties());
        properties.forEach((k, v) -> builder.append(k).append("=").append(v).append("\n"));
        builder.append("\n");

        builder.append("*** System environment variables: \n");
        Map<String, String> env = new TreeMap<>(System.getenv());
        env.forEach((k, v) -> builder.append(k).append("=").append(v).append("\n"));
        builder.append("\n");

        String osDetails = getOsDetails();
        if (osDetails != null) {
            builder.append("*** Operating system: \n");
            builder.append(osDetails);
        }

        return builder.toString();
    }

    /** Returns a string containing details about the running OS. */
    private static String getOsDetails() {
        // Windows: Return full version including build number
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return ProcessHelper.launch(new ProcessBuilder("cmd.exe", "/c", "ver"));
        }

        // No specific information to display
        return null;
    }

    /** Returns the version of the remove BDdeploy server */
    private static String getServerVersion(ClickAndStartDescriptor config) {
        try {
            return LauncherTool.getServerVersion(config).toString();
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

}
