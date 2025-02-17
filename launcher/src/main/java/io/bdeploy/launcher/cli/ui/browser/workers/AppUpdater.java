package io.bdeploy.launcher.cli.ui.browser.workers;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.logging.audit.RollingFileAuditor;

/**
 * A worker that updates the application
 */
public class AppUpdater extends SwingWorker<Integer, Void> {

    private static final Logger log = LoggerFactory.getLogger(AppUpdater.class);

    private final LauncherPathProvider lpp;
    private final Auditor auditor;
    private final List<String> args;
    private final ClientSoftwareConfiguration app;

    public AppUpdater(LauncherPathProvider lpp, Auditor auditor, ClientSoftwareConfiguration app, List<String> args) {
        this.lpp = lpp;
        this.auditor = auditor;
        this.app = app;
        this.args = args;
    }

    @Override
    protected Integer doInBackground() throws Exception {
        Path launcher = ClientPathHelper.getNativeLauncher(lpp);
        Path launchFile = ClientPathHelper.getOrCreateClickAndStart(lpp, app.clickAndStart);

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
        int result = process.waitFor();

        log.info("Fetching configurations...");
        int i = 0;
        Path hivePath = lpp.get(SpecialDirectory.BHIVE);
        try (BHive hive = new BHive(hivePath.toUri(), auditor != null ? auditor : RollingFileAuditor.getFactory().apply(hivePath),
                new ActivityReporter.Null())) {
            try {
                log.info("Updating {}", app.clickAndStart.applicationId);
                setProgress(i++);
                AppRefresher.doUpdate(hive, app);
            } catch (Exception ex) {
                log.error("Failed to fetch configuration", ex);
            }
        }
        log.info("Fetching done.");

        return result;
    }

    @Override
    protected void done() {
        try {
            int exitCode = get();
            if (exitCode != 0) {
                showErrorMessageDialog("Update failed with exit code " + exitCode + ". Check logs for more details.");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            showErrorMessageDialog("Failed to update application: " + ie.getMessage());
        } catch (Exception ex) {
            showErrorMessageDialog("Failed to update application: " + ex.getMessage());
        }
    }

    private static void showErrorMessageDialog(String text) {
        JOptionPane.showMessageDialog(null, text, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
