package io.bdeploy.launcher.cli.ui;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.cli.LauncherTool;
import io.bdeploy.launcher.cli.ProcessHelper;
import io.bdeploy.launcher.cli.SoftwareUpdateException;

/**
 * Provides static helpers to display a {@linkplain MessageDialog}
 */
public class MessageDialogs {

    private static final Logger log = LoggerFactory.getLogger(MessageDialogs.class);
    private static final int ICON_SIZE = 32;

    private MessageDialogs() {
    }

    /**
     * Opens the dialog to show that a required update is available but cannot be installed.
     */
    public static void showUpdateRequired(ClickAndStartDescriptor config, SoftwareUpdateException ex) {
        MessageDialog dialog = new MessageDialog("Software Update");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/update.png", ICON_SIZE, ICON_SIZE));
        dialog.setHeaderText("Software Update Required");
        dialog.setSummary("<html>A required software update is available " +  //
                "but cannot be installed due to insufficient permissions. " + //
                "Contact the system administrator.</html>");
        dialog.setDetailsSummary(ex.getMessage());
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
        dialog.waitForExit();
    }

    /**
     * Opens the dialog to show that launching the given application failed.
     */
    public static void showLaunchFailed(ClickAndStartDescriptor config, Throwable ex) {
        MessageDialog dialog = new MessageDialog("Error");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/error.png", ICON_SIZE, ICON_SIZE));
        dialog.setHeaderText("Application could not be launched");
        dialog.setSummary("<html>Unexpected error occurred while launching the application. " + //
                "If the problem persists, contact the system administrator.</html>");
        dialog.setDetailsSummary(ex.getMessage());
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
        dialog.waitForExit();
    }

    /**
     * Opens the dialog to show uninstallation failed.
     */
    public static void showUninstallationFailed(ClickAndStartDescriptor config, Throwable ex) {
        MessageDialog dialog = new MessageDialog("Error");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/error.png", ICON_SIZE, ICON_SIZE));
        dialog.setHeaderText("Application could not be uninstalled");
        dialog.setSummary("<html>Unexpected error occurred while uninstalling the application. " + //
                "If the problem persists, contact the system administrator.</html>");
        dialog.setDetailsSummary(ex.getMessage());
        dialog.setDetails(getDetailedErrorMessage(config, ex));
        dialog.setVisible(true);
    }

    /**
     * Opens the dialog to show a message stating that the server is now a now, application needs to be re-downloaded.
     */
    public static void showServerIsNode() {
        MessageDialog dialog = new MessageDialog("Server no longer available");
        dialog.setHeaderIcon(WindowHelper.loadIcon("/refresh.png", ICON_SIZE, ICON_SIZE));
        dialog.setHeaderText("Server no longer available");
        dialog.setSummary("<html>The server has been migrated to a different location. " + //
                "You need to re-download the application from the new location.</html>");
        dialog.setVisible(true);
        dialog.setDetails("There are no further details available.");
        dialog.waitForExit();
    }

    /**
     * Opens a dialog to show the given multi-line result.
     */
    public static void showDetailedMessage(String message) {
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(480, 320));
        JOptionPane.showMessageDialog(null, scrollPane, "Result", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Returns the detailed error message to be displayed */
    private static String getDetailedErrorMessage(ClickAndStartDescriptor config, Throwable ex) {
        StringBuilder builder = new StringBuilder();

        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        builder.append("*** Stacktrace: \n").append(writer);
        builder.append("\n");

        builder.append("*** BDeploy properties: \n");
        builder.append("LauncherVersion=").append(VersionHelper.getVersion()).append("\n");
        if (config != null) {
            builder.append("ServerVersion=").append(LauncherTool.getServerVersion(config).toString()).append("\n");
            builder.append("ApplicationId=").append(config.applicationId).append("\n");
            builder.append("GroupId=").append(config.groupId).append("\n");
            builder.append("InstanceId=").append(config.instanceId).append("\n");
            builder.append("Host=").append(config.host.getUri()).append("\n");
            builder.append("Token=").append(config.host.getAuthPack()).append("\n");
        }
        builder.append("\n");

        builder.append("*** Date: \n").append(FormatHelper.formatDate(new Date())).append("\n");
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
        // Windows: Return full OS details if possible.
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            Path winDir = Paths.get(System.getenv("SYSTEMROOT"));
            Path sys32Dir = winDir.resolve("System32");
            Path sysInfo = sys32Dir.resolve("systeminfo.exe");

            if (!Files.isDirectory(sys32Dir) || !Files.isExecutable(sysInfo)) {
                log.info("Cannot get Windows Version details, systeminfo.exe not executable at {}", sysInfo);
                return null;
            }

            try {
                // use absolute path to the utility - no PATH involved.
                return ProcessHelper.launch(new ProcessBuilder(sysInfo.toAbsolutePath().toString()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("Unable to gather Windows OS details", ie);
            } catch (Exception e) {
                log.info("Unable to gather Windows OS details", e);
            }
        }

        // No specific information to display
        return null;
    }
}
