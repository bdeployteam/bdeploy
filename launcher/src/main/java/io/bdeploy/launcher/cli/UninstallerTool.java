package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JOptionPane;

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
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.UninstallerTool.UninstallerConfig;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;
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

        @Help(value = "Don't ask for confirmation before uninstalling application", arg = false)
        boolean yes() default false;

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
        if (!config.yes() && !confirmDelete()) {
            return createResultWithErrorMessage("Aborted, no confirmation");
        }

        Path rootDir = Paths.get(config.homeDir()).toAbsolutePath();
        Path bhiveDir = rootDir.resolve("bhive");
        try (BHive hive = new BHive(bhiveDir.toUri(), RollingFileAuditor.getFactory().apply(bhiveDir),
                new ActivityReporter.Null())) {
            doUninstall(rootDir, hive, config.app());
        }
        return createSuccess();
    }

    private boolean confirmDelete() {
        int result = JOptionPane.showConfirmDialog(null,
                "Are you sure you want to delete this application? This CANNOT be undone.", "Uninstall",
                JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }

    /** Uninstall the given application and removes all not required artifacts */
    private void doUninstall(Path rootDir, BHive hive, String appId) {
        try {
            hive.execute(new DirectoryLockOperation().setDirectory(rootDir));

            log.info("Removing application {}", appId);
            Path appsDir = rootDir.resolve("apps");
            Path poolDir = appsDir.resolve(SpecialDirectory.MANIFEST_POOL.getDirName());
            Path startScriptsDir = appsDir.resolve(SpecialDirectory.START_SCRIPTS.getDirName());
            Path fileAssocScriptsDir = appsDir.resolve(SpecialDirectory.FILE_ASSOC_SCRIPTS.getDirName());

            // Delegate removal to the delegated application
            ClientSoftwareManifest cmf = new ClientSoftwareManifest(hive);
            ClientSoftwareConfiguration config = cmf.readNewest(appId, false);
            if (config != null && config.launcher != null) {
                Version version = VersionHelper.tryParse(config.launcher.getTag());
                doUninstallVersioned(rootDir, version, appId);
            } else {
                doUninstallApp(rootDir, appId);
            }

            // Remove corresponding scripts
            if (config != null) {
                ClientApplicationDto metadata = config.metadata;
                if (metadata != null) {
                    OperatingSystem os = OsHelper.getRunningOs();
                    ClickAndStartDescriptor descriptor = config.clickAndStart;

                    String startScriptName = metadata.startScriptName;
                    String fileAssocExtension = metadata.fileAssocExtension;

                    String startScriptIdentifier = ScriptUtils.getStartScriptIdentifier(os, startScriptName);
                    String fileAssocIdentifier = ScriptUtils.getFileAssocIdentifier(os, fileAssocExtension);

                    removeScript(hive, descriptor, startScriptName, startScriptsDir, "start",//
                            settings -> settings.getStartScriptInfo(startScriptIdentifier),//
                            settings -> settings.removeStartScriptInfo(startScriptIdentifier),//
                            null);
                    removeScript(hive, descriptor, fileAssocExtension, fileAssocScriptsDir, "file association",//
                            settings -> settings.getFileAssocScriptInfo(fileAssocIdentifier),//
                            settings -> settings.removeFileAssocScriptInfo(fileAssocIdentifier),//
                            scriptPath -> uninstallFileAssoc(metadata, rootDir));
                }
            }

            // Remove the manifest which software is used by this application
            if (cmf.remove(appId)) {
                log.info("Removed software manifest.");
            }

            // Trigger cleanup to remove from hive and from pool
            ClientCleanup cleanup = new ClientCleanup(hive, rootDir, appsDir, poolDir, startScriptsDir, fileAssocScriptsDir);
            cleanup.run();
        } finally {
            hive.execute(new DirectoryReleaseOperation().setDirectory(rootDir));
        }
    }

    private static void removeScript(BHive hive, ClickAndStartDescriptor descriptor, String scriptName, Path scriptsDir,
            String scriptType, Function<LocalClientApplicationSettings, ScriptInfo> scriptInfoExtractor,
            Consumer<LocalClientApplicationSettings> settingsUpdater, Consumer<Path> uninstallationAddon) {
        if (StringHelper.isNullOrBlank(scriptName)) {
            return;
        }

        LocalClientApplicationSettingsManifest manifest = new LocalClientApplicationSettingsManifest(hive);
        LocalClientApplicationSettings settings = manifest.read();

        ScriptInfo scriptInfo = scriptInfoExtractor.apply(settings);
        if (scriptInfo != null && descriptor.equals(scriptInfo.getDescriptor())) {
            Path scriptPath = scriptsDir.resolve(scriptInfo.getScriptName());
            if (PathHelper.exists(scriptPath)) {
                PathHelper.deleteRecursiveRetry(scriptPath);
                log.info("Removed {} script {}", scriptType, scriptName);
            }
            settingsUpdater.accept(settings);
            manifest.write(settings);
            if (uninstallationAddon != null) {
                uninstallationAddon.accept(scriptPath);
            }
        }
    }

    private static void uninstallFileAssoc(ClientApplicationDto metadata, Path launcherDir) {
        String id = metadata.id;
        if (id == null) {
            log.error("Failed to remove file association because no ID was given.");
            return;
        }
        String fileAssocExtension = metadata.fileAssocExtension;
        if (fileAssocExtension == null) {
            log.error("Failed to remove file association for {} because no file association extension was given.", id);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(ClientPathHelper.getNativeFileAssocTool(launcherDir).toString());
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            command.add("/UninstallApplication");
            command.add(ScriptUtils.getBDeployFileAssocId(id));
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
        } else {
            // Usage: ./file-assoc.sh "Action" "ID" "Extension" "Name of Application" "Exec-Path" "Icon"
            command.add("remove");
            command.add(id);
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
            command.add(metadata.appName);
        }

        ProcessBuilder b = new ProcessBuilder(command);
        // We are not interested in the output
        b.redirectOutput(Redirect.DISCARD);
        b.redirectError(Redirect.DISCARD);

        try {
            b.start();
        } catch (IOException e) {
            log.error("Failed to remove file association for {} ({})", fileAssocExtension, id, e);
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
