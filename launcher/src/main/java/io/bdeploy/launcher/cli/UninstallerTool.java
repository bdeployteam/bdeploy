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
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
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

        @Help("Directory where the launcher stores the hive as well as all applications. ")
        String homeDir();

        @Help("The unique identifier of the application to uninstall.")
        String app();

        @Help(value = "Don't ask for confirmation before uninstalling application", arg = false)
        boolean yes() default false;
    }

    /** The {@link LauncherPathProvider} for this {@link UninstallerTool} */
    private LauncherPathProvider lpp;
    private Path homeDir;
    private Path bhiveDir;
    private Path startScriptsDir;
    private Path fileAssocScriptsDir;
    private Path appDir;

    public UninstallerTool() {
        super(UninstallerConfig.class);
    }

    @Override
    protected RenderableResult run(UninstallerConfig config) {
        String homeDirString = config.homeDir();
        if (homeDirString == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }
        String instanceId = config.app();
        if (instanceId == null) {
            throw new IllegalStateException("Missing --app argument");
        }
        if (!config.yes() && !confirmDelete()) {
            return createResultWithErrorMessage("Aborted, no confirmation");
        }

        lpp = new LauncherPathProvider(Paths.get(homeDirString)).setInstance(config.app());
        homeDir = lpp.get(SpecialDirectory.HOME);
        bhiveDir = lpp.get(SpecialDirectory.BHIVE);
        startScriptsDir = lpp.get(SpecialDirectory.START_SCRIPTS);
        fileAssocScriptsDir = lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS);
        appDir = lpp.get(SpecialDirectory.APP);

        try (BHive hive = new BHive(bhiveDir.toUri(), RollingFileAuditor.getFactory().apply(bhiveDir),
                new ActivityReporter.Null())) {
            doUninstall(hive, instanceId);
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
    private void doUninstall(BHive hive, String appId) {
        try {
            hive.execute(new DirectoryLockOperation().setDirectory(homeDir));

            log.info("Removing application {}", appId);

            // Delegate removal to the delegated application
            ClientSoftwareManifest cmf = new ClientSoftwareManifest(hive);
            ClientSoftwareConfiguration config = cmf.readNewest(appId, false);
            if (config != null && config.launcher != null) {
                Version version = VersionHelper.tryParse(config.launcher.getTag());
                doUninstallVersioned(homeDir, version, appId);
            } else {
                doUninstallApp(appId);
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
                            scriptPath -> uninstallFileAssoc(metadata, homeDir));
                }
            }

            // Remove the manifest which software is used by this application
            if (cmf.remove(appId)) {
                log.info("Removed software manifest.");
            }

            // Trigger cleanup to remove from hive and from pool
            new ClientCleanup(hive, lpp).run();
        } finally {
            hive.execute(new DirectoryReleaseOperation().setDirectory(homeDir));
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
        command.add(ClientPathHelper.getNativeFileAssocTool(new LauncherPathProvider(launcherDir)).toString());
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
    private void doUninstallApp(String appId) {
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
    private void doUninstallVersioned(Path homeDir, Version version, String appId) {
        Path versionedHome = ClientPathHelper.getHome(homeDir, version);
        Path versionedAppDir = new LauncherPathProvider(versionedHome).get(SpecialDirectory.APP, appId);
        if (PathHelper.exists(versionedAppDir)) {
            PathHelper.deleteRecursiveRetry(versionedAppDir);
            log.info("Removed application folder {}", versionedAppDir);
        }
    }
}
