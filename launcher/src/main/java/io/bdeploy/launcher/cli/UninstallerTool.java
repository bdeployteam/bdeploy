package io.bdeploy.launcher.cli;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.launcher.cli.UninstallerTool.UninstallerConfig;

@CliName("uninstaller")
@Help("A tool which uninstalls a client application.")
public class UninstallerTool extends ConfiguredCliTool<UninstallerConfig> {

    private static final Logger log = LoggerFactory.getLogger(UninstallerTool.class);

    public @interface UninstallerConfig {

        @Help("The unique identifier of the application to uninstall.")
        String app();

    }

    public UninstallerTool() {
        super(UninstallerConfig.class);
    }

    @Override
    protected void run(UninstallerConfig config) {
        if (config.app() == null) {
            throw new IllegalStateException("Missing --app argument");
        }
        Path rootDir = ClientPathHelper.getBDeployHome();
        Path bhiveDir = rootDir.resolve("bhive");
        try (BHive hive = new BHive(bhiveDir.toUri(), new ActivityReporter.Null())) {
            doUninstall(rootDir, hive, config.app());
        }
    }

    /** Uninstall the given application and removes all not required artifacts */
    private void doUninstall(Path rootDir, BHive hive, String appUid) {
        Path appsDir = rootDir.resolve("apps");
        Path poolDir = appsDir.resolve("pool");
        log.info("Removing application {}", appUid);

        // Delete the directory where application specific files are stored
        Path appDir = appsDir.resolve(appUid);
        if (appDir.toFile().exists()) {
            PathHelper.deleteRecursive(appDir);
            log.info("Removed application folder {}", appDir);
        }

        // Remove the manifest which software is used by this application
        ClientSoftwareManifest cmf = new ClientSoftwareManifest(hive);
        if (cmf.remove(appUid)) {
            log.info("Removed software manifest.");
        }

        // Trigger cleanup to remove from hive and from pool
        ClientAppCleanup cleanup = new ClientAppCleanup(hive, poolDir);
        cleanup.run();

        // Remove pool and apps directory if they are empty
        if (PathHelper.isDirEmpty(poolDir)) {
            PathHelper.deleteRecursive(poolDir);
        }
        if (PathHelper.isDirEmpty(appsDir)) {
            PathHelper.deleteRecursive(appsDir);
        }
    }

}
