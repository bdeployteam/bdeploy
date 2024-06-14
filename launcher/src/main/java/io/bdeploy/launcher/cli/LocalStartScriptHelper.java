package io.bdeploy.launcher.cli;

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
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.StartScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;

public class LocalStartScriptHelper {

    private final Auditor auditor;
    private final Path rootDir;
    private final Path appDir;
    private final Path startScriptsDir;

    public LocalStartScriptHelper(Auditor auditor, Path rootDir, Path appDir, Path startScriptsDir) {
        this.auditor = auditor;
        this.rootDir = rootDir;
        this.appDir = appDir;
        this.startScriptsDir = startScriptsDir;
    }

    public void createStartScript(String scriptName, ClickAndStartDescriptor clickAndStart, boolean override) throws IOException {
        OperatingSystem os = OsHelper.getRunningOs();
        String fullScriptName = getStartFullScriptName(os, scriptName);
        String scriptContent = getStartScriptContent(os);
        Path fullScriptPath = startScriptsDir.resolve(fullScriptName);

        Files.createDirectories(startScriptsDir);
        if (override) {
            Files.deleteIfExists(fullScriptPath);
        }
        Files.writeString(fullScriptPath, scriptContent, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        setExecutable(fullScriptPath);

        updateSettings(scriptName, fullScriptName, clickAndStart, override);
    }

    private String getStartFullScriptName(OperatingSystem os, String startScriptName) {
        if (os == OperatingSystem.WINDOWS) {
            String fileExtension = ".bat";
            return startScriptName.endsWith(fileExtension) ? startScriptName : startScriptName + fileExtension;
        }
        return startScriptName;
    }

    private String getStartScriptContent(OperatingSystem os) {
        if (os == OperatingSystem.WINDOWS) {
            return "@echo off\n"//
                    + '"' + ClientPathHelper.getScriptLauncher(rootDir).toAbsolutePath() + "\" \"launcher\" \"--launch="
                    + appDir.resolve(ClientPathHelper.LAUNCH_FILE_NAME).toAbsolutePath() + "\" \"--homeDir="
                    + rootDir.toAbsolutePath() + "\" --noSplash -- %*";
        }
        return "#!/usr/bin/env bash\n" //
                + ClientPathHelper.getScriptLauncher(rootDir).toAbsolutePath() + " launcher --launch="
                + appDir.resolve(ClientPathHelper.LAUNCH_FILE_NAME).toAbsolutePath() + " --noSplash -- \"$@\"";
    }

    private void updateSettings(String name, String fullName, ClickAndStartDescriptor clickAndStart, boolean override) {
        try (BHive hive = new BHive(rootDir.resolve("bhive").toUri(), auditor, new ActivityReporter.Null())) {
            LocalClientApplicationSettingsManifest settingsManifest = new LocalClientApplicationSettingsManifest(hive);
            LocalClientApplicationSettings settings = settingsManifest.read();
            if (settings == null) {
                settings = new LocalClientApplicationSettings();
            }
            settings.putStartScriptInfo(name, new StartScriptInfo(fullName, clickAndStart), override);
            settingsManifest.write(settings);
        }
    }

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
