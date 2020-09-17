package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

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

        @Help("Directory where the launcher stores the hive as well as all applications. ")
        String homeDir();

    }

    public UninstallerTool() {
        super(UninstallerConfig.class);
    }

    @Override
    protected RenderableResult run(UninstallerConfig config) {
        if (config.homeDir() == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }
        if (config.app() == null) {
            throw new IllegalStateException("Missing --app argument");
        }

        Path rootDir = Paths.get(config.homeDir()).toAbsolutePath();
        Path bhiveDir = rootDir.resolve("bhive");
        MarkerDatabase.lockRoot(rootDir, null, null);
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
            doUninstallVersioned(rootDir, version, appUid);
        } else {
            doUninstallApp(rootDir, appUid);
        }

        // Remove the manifest which software is used by this application
        if (cmf.remove(appUid)) {
            log.info("Removed software manifest.");
        }

        // Trigger cleanup to remove from hive and from pool
        ClientCleanup cleanup = new ClientCleanup(hive, rootDir, appsDir, poolDir);
        cleanup.run();
    }

    /**
     * Removes the given application from this hive and from the pool
     */
    private void doUninstallApp(Path rootDir, String appUid) {
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
     * Removes the application stored in the given version specific directory
     */
    private void doUninstallVersioned(Path rootDir, Version version, String appUid) {
        Path versionedRoot = ClientPathHelper.getHome(rootDir, version);
        Path appsDir = versionedRoot.resolve("apps");
        Path appDir = appsDir.resolve(appUid);
        if (appDir.toFile().exists()) {
            PathHelper.deleteRecursive(appDir);
            log.info("Removed application folder {}", appDir);
        }
    }

}
