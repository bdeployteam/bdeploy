package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.DirectoryLockOperation;
import io.bdeploy.bhive.op.DirectoryReleaseOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.StartScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.UninstallerTool.UninstallerConfig;
import io.bdeploy.logging.audit.RollingFileAuditor;

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
        try (BHive hive = new BHive(bhiveDir.toUri(), RollingFileAuditor.getFactory().apply(bhiveDir),
                new ActivityReporter.Null())) {
            doUninstall(rootDir, hive, config.app());
        }
        return createSuccess();
    }

    /** Uninstall the given application and removes all not required artifacts */
    private void doUninstall(Path rootDir, BHive hive, String appId) {
        try {
            hive.execute(new DirectoryLockOperation().setDirectory(rootDir));

            log.info("Removing application {}", appId);
            Path appsDir = rootDir.resolve("apps");
            Path poolDir = appsDir.resolve(SpecialDirectory.MANIFEST_POOL.getDirName());
            Path startScriptsDir = appsDir.resolve(SpecialDirectory.START_SCRIPTS.getDirName());

            // Delegate removal to the delegated application
            ClientSoftwareManifest cmf = new ClientSoftwareManifest(hive);
            ClientSoftwareConfiguration config = cmf.readNewest(appId, false);
            if (config != null && config.launcher != null) {
                Version version = VersionHelper.tryParse(config.launcher.getTag());
                doUninstallVersioned(rootDir, version, appId);
            } else {
                doUninstallApp(rootDir, appId);
            }

            // Remove corresponding script
            if (config != null) {
                ClientApplicationDto metadata = config.metadata;
                if (metadata != null) {
                    String startScriptName = metadata.startScriptName;
                    if (!StringHelper.isNullOrBlank(startScriptName)) {
                        LocalClientApplicationSettings settings = new LocalClientApplicationSettingsManifest(hive).read();
                        if (settings != null) {
                            StartScriptInfo startScriptInfo = settings.getStartScriptInfo(startScriptName);
                            if (startScriptInfo != null && config.clickAndStart.equals(startScriptInfo.getDescriptor())) {
                                Path startScript = startScriptsDir.resolve(startScriptInfo.getFullScriptName());
                                if (PathHelper.exists(startScript)) {
                                    PathHelper.deleteRecursiveRetry(startScript);
                                    log.info("Removed script {}", startScriptName);
                                }
                            }
                        }
                    }
                }
            }

            // Remove the manifest which software is used by this application
            if (cmf.remove(appId)) {
                log.info("Removed software manifest.");
            }

            // Trigger cleanup to remove from hive and from pool
            ClientCleanup cleanup = new ClientCleanup(hive, rootDir, appsDir, poolDir, startScriptsDir);
            cleanup.run();
        } finally {
            hive.execute(new DirectoryReleaseOperation().setDirectory(rootDir));
        }
    }

    /**
     * Removes the given application from this hive and from the pool
     */
    private void doUninstallApp(Path rootDir, String appId) {
        Path appsDir = rootDir.resolve("apps");
        Path appDir = appsDir.resolve(appId);
        if (PathHelper.exists(appDir)) {
            PathHelper.deleteRecursiveRetry(appDir);
            log.info("Removed application folder {}", appDir);
        } else {
            log.info("Application {} is not installed.", appId);
        }
    }

    /**
     * Removes the application stored in the given version specific directory
     */
    private void doUninstallVersioned(Path rootDir, Version version, String appId) {
        Path versionedRoot = ClientPathHelper.getHome(rootDir, version);
        Path appsDir = versionedRoot.resolve("apps");
        Path appDir = appsDir.resolve(appId);
        if (PathHelper.exists(appDir)) {
            PathHelper.deleteRecursiveRetry(appDir);
            log.info("Removed application folder {}", appDir);
        }
    }
}
