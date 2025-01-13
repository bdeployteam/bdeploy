package io.bdeploy.launcher.cli.ui.browser.workers;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

/**
 * A worker that launches the application
 */
public class AppLauncher extends AppRefresher {

    private static final Logger log = LoggerFactory.getLogger(AppLauncher.class);

    private final ClientSoftwareConfiguration app;
    private final List<String> args;
    private final boolean doRefresh;

    public AppLauncher(LauncherPathProvider lpp, Auditor auditor, ClientSoftwareConfiguration app, List<String> args,
            boolean doRefresh) {
        super(lpp, auditor, List.of(app));
        this.app = app;
        this.args = args;
        this.doRefresh = doRefresh;
    }

    @Override
    protected Void doInBackground() throws IOException {
        if (doRefresh) {
            super.doInBackground();
        }

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
