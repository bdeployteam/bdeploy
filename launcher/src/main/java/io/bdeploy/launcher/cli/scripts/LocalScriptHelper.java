package io.bdeploy.launcher.cli.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;

public abstract class LocalScriptHelper {

    protected final OperatingSystem os;
    protected final LauncherPathProvider lpp;
    private final Auditor auditor;
    private final Path scriptDir;

    protected LocalScriptHelper(OperatingSystem os, Auditor auditor, LauncherPathProvider lpp, SpecialDirectory scriptDir) {
        this.os = os;
        this.lpp = lpp;
        this.auditor = auditor;
        this.scriptDir = lpp.get(scriptDir);
    }

    /**
     * Creates a script on the file system.
     *
     * @param override Whether to override existing scripts
     * @throws IOException If override is <code>false</code> and a different application is already using the same identifier
     */
    public void createScript(ClientApplicationDto metadata, ClickAndStartDescriptor clickAndStart, boolean override)
            throws IOException {
        String scriptName = calculateScriptName(metadata);
        if (StringHelper.isNullOrBlank(scriptName)) {
            return;
        }

        String scriptContent = getScriptContent();
        Path fullScriptPath = scriptDir.resolve(scriptName);

        Files.createDirectories(scriptDir);
        if (override) {
            Files.deleteIfExists(fullScriptPath);
        }
        Files.writeString(fullScriptPath, scriptContent, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        setExecutable(fullScriptPath);

        try (BHive hive = new BHive(lpp.get(SpecialDirectory.BHIVE).toUri(), auditor, new ActivityReporter.Null())) {
            LocalClientApplicationSettingsManifest settingsManifest = new LocalClientApplicationSettingsManifest(hive);
            LocalClientApplicationSettings settings = settingsManifest.read();
            updateSettings(settings, scriptName, new ScriptInfo(scriptName, clickAndStart), override);
            settingsManifest.write(settings);
        }

        afterUpdateHook(metadata, fullScriptPath);
    }

    protected void afterUpdateHook(ClientApplicationDto metadata, Path fullScriptPath) {
        // Do nothing by default
    }

    public abstract String calculateScriptName(ClientApplicationDto metadata);

    protected abstract String getScriptContent();

    protected abstract void updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo,
            boolean override);

    private static void setExecutable(Path p) throws IOException {
        PosixFileAttributeView view = PathHelper.getPosixView(p);
        if (view != null) {
            Set<PosixFilePermission> perms = view.readAttributes().permissions();
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            view.setPermissions(perms);
        }
    }
}
