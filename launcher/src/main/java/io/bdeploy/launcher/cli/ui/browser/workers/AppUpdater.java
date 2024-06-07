package io.bdeploy.launcher.cli.ui.browser.workers;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

/**
 * A worker that updates the application
 */
public class AppUpdater extends SwingWorker<Integer, Void> {

    private final Path rootDir;
    private final List<String> args;
    private final ClientSoftwareConfiguration app;

    public AppUpdater(Path rootDir, ClientSoftwareConfiguration app, List<String> args) {
        this.rootDir = rootDir;
        this.args = args;
        this.app = app;
    }

    @Override
    protected Integer doInBackground() throws Exception {
        Path launchFile = ClientPathHelper.getOrCreateClickAndStart(rootDir, app.clickAndStart);
        Path launcher = ClientPathHelper.getNativeLauncher(rootDir);

        List<String> command = new ArrayList<>();
        command.add(launcher.toFile().getAbsolutePath());
        command.add(launchFile.toFile().getAbsolutePath());
        command.addAll(args);
        ProcessBuilder b = new ProcessBuilder(command);
        // We are not interested in the output
        b.redirectOutput(Redirect.DISCARD);
        b.redirectError(Redirect.DISCARD);

        Process process = b.start();

        // Wait for termination
        return process.waitFor();
    }

    @Override
    protected void done() {
        try {
            int exitCode = get();
            if (exitCode != 0) {
                JOptionPane.showMessageDialog(null, "Update failed with exit code " + exitCode + ". Check logs for more details.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(null, "Failed to update application: " + ie.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Failed to update application: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

}
