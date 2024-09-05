package io.bdeploy.launcher.cli.ui.browser.workers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ProcessHelper;
import io.bdeploy.launcher.cli.ui.MessageDialogs;

/**
 * A worker that invokes the native uninstaller and waits for its completion
 */
public class AppUninstaller extends SwingWorker<Void, Object> {

    private static final Logger log = LoggerFactory.getLogger(AppUninstaller.class);

    private final LauncherPathProvider lpp;
    private final ClientSoftwareConfiguration app;

    public AppUninstaller(LauncherPathProvider lpp, ClientSoftwareConfiguration app) {
        this.lpp = lpp;
        this.app = app;
    }

    @Override
    protected Void doInBackground() throws Exception {
        log.info("Attempting to uninstall application {}", app.clickAndStart.applicationId);

        ProcessBuilder builder = new ProcessBuilder(getUninstallCommand()).redirectErrorStream(true);
        if (log.isInfoEnabled()) {
            String command = builder.command().stream().collect(Collectors.joining(" "));
            log.info("Executing {}", command);
        }

        Process process = builder.start();
        String output = ProcessHelper.readOutput(process);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Uninstaller terminated with exit code " + exitCode + ".\n" + output);
        }
        log.info("Uninstallation was successful.");
        return null;
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            MessageDialogs.showUninstallationFailed(app.clickAndStart, ie);
        } catch (Exception ex) {
            MessageDialogs.showUninstallationFailed(app.clickAndStart, ex);
        }
    }

    private List<String> getUninstallCommand() throws IOException {
        ClickAndStartDescriptor clickAndStart = app.clickAndStart;

        List<String> command = new ArrayList<>();
        // On Windows the BDeploy executable expects the click and start file
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            Path launcher = ClientPathHelper.getNativeLauncher(lpp);
            Path launchFile = ClientPathHelper.getOrCreateClickAndStart(lpp, clickAndStart);
            command.add(launcher.toString());
            command.add("/Uninstall");
            command.add(launchFile.toString());
            return command;
        }

        // On Linux the installer writes a special uninstall script
        // ATTENTION: This is not there if the application has been launched via click&start
        String appId = clickAndStart.applicationId;
        Path uninstaller = lpp.get(SpecialDirectory.APP, appId).resolve("uninstall.run");
        if (PathHelper.exists(uninstaller)) {
            command.add(uninstaller.toString());
            return command;
        }

        // Startup the native launcher and pass the uninstall arguments
        Path launcher = ClientPathHelper.getNativeLauncher(lpp);
        command.add(launcher.toString());
        command.add("uninstaller");
        command.add("--app=" + appId);
        return command;
    }
}
