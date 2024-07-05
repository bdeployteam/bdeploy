package io.bdeploy.launcher.cli.ui.browser.workers;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

/**
 * A worker that launches the application
 */
public class AppLauncher extends SwingWorker<Object, Void> {

    private static final Logger log = LoggerFactory.getLogger(AppLauncher.class);

    private final LauncherPathProvider lpp;
    private final List<String> args;
    private final ClientSoftwareConfiguration app;

    public AppLauncher(LauncherPathProvider lpp, ClientSoftwareConfiguration app, List<String> args) {
        this.lpp = lpp;
        this.app = app;
        this.args = args;
    }

    @Override
    protected Object doInBackground() throws Exception {
        Path launcher = ClientPathHelper.getNativeLauncher(lpp);
        Path launchFile = ClientPathHelper.getOrCreateClickAndStart(lpp, app.clickAndStart);

        List<String> command = new ArrayList<>();
        command.add(launcher.toFile().getAbsolutePath());
        command.add(launchFile.toFile().getAbsolutePath());
        command.addAll(args);
        ProcessBuilder b = new ProcessBuilder(command);
        b.redirectError(Redirect.DISCARD);
        b.redirectOutput(Redirect.DISCARD);
        b.start();
        return null;
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(null, "Failed to launch application: " + ie.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            log.warn("Failed to launch application", ex);
            JOptionPane.showMessageDialog(null, "Failed to launch application: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
