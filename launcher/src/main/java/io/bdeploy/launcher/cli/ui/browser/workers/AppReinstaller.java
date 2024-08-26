package io.bdeploy.launcher.cli.ui.browser.workers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ProcessHelper;
import io.bdeploy.launcher.cli.ui.MessageDialogs;

/**
 * A worker that reinstalls the selected application
 */
public class AppReinstaller extends SwingWorker<Void, Object> {

    private static final Logger log = LoggerFactory.getLogger(AppReinstaller.class);

    private final LauncherPathProvider lpp;
    private final ClientSoftwareConfiguration app;

    public AppReinstaller(LauncherPathProvider lpp, ClientSoftwareConfiguration app) {
        this.lpp = lpp;
        this.app = app;
    }

    @Override
    protected Void doInBackground() throws Exception {
        uninstall();
        install();
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

    private void uninstall() {
        log.info("Attempting to uninstall application {}", app.clickAndStart.applicationId);

        Path appPoolDir = getAppPoolDir();
        log.info("Deleting app from pool {}", appPoolDir);
        PathHelper.deleteRecursiveRetry(appPoolDir);

        Path appDir = lpp.get(SpecialDirectory.APP, app.clickAndStart.applicationId);
        log.info("Deleting app folder {}", appDir);
        PathHelper.deleteRecursiveRetry(appDir);
    }

    private Path getAppPoolDir() {
        MasterRootResource master = ResourceProvider.getVersionedResource(app.clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(app.clickAndStart.groupId);
        ClientApplicationConfiguration clientAppCfg = namedMaster.getClientConfiguration(app.clickAndStart.instanceId,
                app.clickAndStart.applicationId);
        return lpp.get(SpecialDirectory.MANIFEST_POOL).resolve(clientAppCfg.appConfig.application.directoryFriendlyName());
    }

    private void install() throws IOException, InterruptedException {
        log.info("Attempting to install application {}", app.clickAndStart.applicationId);
        ProcessBuilder builder = new ProcessBuilder(getInstallCommand()).redirectErrorStream(true);
        if (log.isInfoEnabled()) {
            String command = builder.command().stream().collect(Collectors.joining(" "));
            log.info("Executing {}", command);
        }

        Process process = builder.start();
        String output = ProcessHelper.readOutput(process);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Installer terminated with exit code " + exitCode + ".\n" + output);
        }
        log.info("Successfully reinstalled application {}.", app.clickAndStart.applicationId);
    }

    private List<String> getInstallCommand() throws IOException {
        List<String> command = new ArrayList<>();
        Path launcher = ClientPathHelper.getNativeLauncher(lpp);
        Path launchFile = ClientPathHelper.getOrCreateClickAndStart(lpp, app.clickAndStart);
        command.add(launcher.toFile().getAbsolutePath());
        command.add(launchFile.toFile().getAbsolutePath());
        command.add("--updateOnly");
        return command;

    }
}
