package io.bdeploy.launcher.cli.ui.browser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

/**
 * A worker that launches the application
 */
public class AppLauncher extends SwingWorker<Object, Void> {

    private final Path rootDir;
    private final List<String> args;
    private final ClientSoftwareConfiguration app;

    public AppLauncher(Path rootDir, ClientSoftwareConfiguration app, List<String> args) {
        this.rootDir = rootDir;
        this.args = args;
        this.app = app;
    }

    @Override
    protected Object doInBackground() throws Exception {
        Path launchFile = ClientPathHelper.getOrCreateClickAndStart(rootDir, app.clickAndStart);
        Path launcher = ClientPathHelper.getNativeLauncher(rootDir);

        List<String> command = new ArrayList<>();
        command.add(launcher.toFile().getAbsolutePath());
        command.add(launchFile.toFile().getAbsolutePath());
        command.addAll(args);
        ProcessBuilder b = new ProcessBuilder(command);
        Process process = b.start();

        // We are not interested in the output
        process.getErrorStream().close();
        process.getInputStream().close();
        process.getOutputStream().close();

        return null;
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Failed to launch application: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

}
