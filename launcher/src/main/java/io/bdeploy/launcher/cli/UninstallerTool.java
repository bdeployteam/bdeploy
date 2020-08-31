package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.launcher.cli.UninstallerTool.UninstallerConfig;

@CliName("uninstaller")
@Help("A tool which uninstalls a client application.")
public class UninstallerTool extends ConfiguredCliTool<UninstallerConfig> {

    private static final Logger log = LoggerFactory.getLogger(UninstallerTool.class);

    public @interface UninstallerConfig {

        @Help("The unique identifier of the application to uninstall.")
        String app();

        @Help("Directory where the launcher stores the hive as well as all applications. Defaults to home/.bdeploy.")
        String homeDir();

    }

    public UninstallerTool() {
        super(UninstallerConfig.class);
    }

    @Override
    protected RenderableResult run(UninstallerConfig config) {
        if (config.app() == null) {
            throw new IllegalStateException("Missing --app argument");
        }
        // Check where to put local data.
        Path rootDir;
        if (config.homeDir() != null && !config.homeDir().isEmpty()) {
            rootDir = Paths.get(config.homeDir());
        } else {
            rootDir = ClientPathHelper.getBDeployHome();
        }
        Path bhiveDir = rootDir.resolve("bhive");
        MarkerDatabase.lockRoot(rootDir);
        try (BHive hive = new BHive(bhiveDir.toUri(), new ActivityReporter.Null())) {
            doUninstall(rootDir, hive, config.app());
        } finally {
            MarkerDatabase.unlockRoot(rootDir);
        }
        return createSuccess();
    }

    /** Uninstall the given application and removes all not required artifacts */
    private void doUninstall(Path rootDir, BHive hive, String appUid) {
        log.info("Removing application {}", appUid);
        Path appsDir = rootDir.resolve("apps");
        Path poolDir = appsDir.resolve("pool");

        // Delegate removal to the delegated application
        ClientSoftwareManifest cmf = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration config = cmf.readNewest(appUid);
        if (config != null && config.launcher != null) {
            Version version = VersionHelper.tryParse(config.launcher.getTag());
            doDelegateUninstall(version, appUid);
        } else {
            doUninstallApp(rootDir, appUid);
        }

        // Remove the manifest which software is used by this application
        if (cmf.remove(appUid)) {
            log.info("Removed software manifest.");
        }

        // Trigger cleanup to remove from hive and from pool
        ClientCleanup cleanup = new ClientCleanup(hive, appsDir, poolDir);
        cleanup.run();
    }

    /**
     * Removes the given application from this hive and from the pool
     */
    private void doUninstallApp(Path rootDir, String appUid) {
        // Delete the directory where application specific files are stored
        Path appsDir = rootDir.resolve("apps");
        Path appDir = appsDir.resolve(appUid);
        if (appDir.toFile().exists()) {
            PathHelper.deleteRecursive(appDir);
            log.info("Removed application folder {}", appDir);
        } else {
            log.info("Application {} is not installed.", appUid);
        }
    }

    /**
     * Delegates the removal to the uninstaller with the given version.
     */
    private void doDelegateUninstall(Version version, String appUid) {
        log.info("Delegating uninstallation of {} to version {}", appUid, version);

        // Invoke uninstaller or at least remove files manually
        Path nativeUninstaller = ClientPathHelper.getNativeUninstaller(version);
        if (!nativeUninstaller.toFile().exists()) {
            doManualUninstallation(version, appUid);
        } else {
            doStartUninstallation(version, appUid);
        }
    }

    /** Triggers the native uninstaller to remove the application */
    private void doStartUninstallation(Version version, String appUid) {
        Path homeDir = ClientPathHelper.getHome(version);
        Path nativeUninstaller = ClientPathHelper.getNativeUninstaller(version);

        List<String> command = new ArrayList<>();
        command.add(nativeUninstaller.toFile().getAbsolutePath());
        command.add(appUid);

        log.info("Executing {}", command.stream().collect(Collectors.joining(" ")));
        try {
            ProcessBuilder b = new ProcessBuilder(command);
            b.redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT).redirectOutput(Redirect.INHERIT);
            b.directory(homeDir.resolve("launcher").toFile());

            // We set explicitly overwrite the default environment variables so that the uninstaller is using
            // the home directory that we specify. Important as the other uninstaller should not use our
            // hive to prevent unintended side-effects.
            Map<String, String> env = b.environment();
            env.put(ClientPathHelper.BDEPLOY_HOME, homeDir.toFile().getAbsolutePath());

            Process process = b.start();
            log.info("Uninstaller successfully launched. PID={}", process.pid());

            int exitCode = process.waitFor();
            log.info("Uninstaller terminated with exit code {}.", exitCode);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start uninstaller.", e);
        } catch (InterruptedException e) {
            log.warn("Waiting for uninstaller interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Manually removes files from the apps directory. The applications still remains in the pool and in the hive.
     */
    private void doManualUninstallation(Version version, String appUid) {
        log.warn("Native uninstaller not available. Trying to manually remove files...");

        // Remove the apps folder
        Path rootDir = ClientPathHelper.getHome(version);
        Path appsDir = rootDir.resolve("apps");
        Path appDir = appsDir.resolve(appUid);
        if (appDir.toFile().exists()) {
            PathHelper.deleteRecursive(appDir);
            log.info("Removed application folder {}", appDir);
        }
    }

}
