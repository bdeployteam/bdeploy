package io.bdeploy.launcher.cli.scripts.impl;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;

public class LocalFileAssocScriptHelper extends LocalScriptHelper {

    private static final Logger log = LoggerFactory.getLogger(LocalFileAssocScriptHelper.class);

    public LocalFileAssocScriptHelper(OperatingSystem os, Auditor auditor, Path launcherDir, Path appDir, Path scriptDir) {
        super(os, auditor, launcherDir, appDir, scriptDir);
    }

    @Override
    protected void afterUpdateHook(ClientApplicationDto metadata, Path fullScriptPath) {
        String id = metadata.id;
        if (id == null) {
            log.error("Failed to create file association because no ID was given.");
            return;
        }
        String fileAssocExtension = metadata.fileAssocExtension;
        if (fileAssocExtension == null) {
            log.error("Failed to create file association for {} because no file association extension was given.", id);
            return;
        }
        String appName = metadata.appName;
        if (appName == null) {
            log.error("Failed to create file association for {} files ({})", fileAssocExtension, id);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(ClientPathHelper.getNativeFileAssocTool(launcherDir).normalize().toAbsolutePath().toString());
        if (os == OperatingSystem.WINDOWS) {
            command.add("/InstallApplication");
            command.add(ScriptUtils.getBDeployFileAssocId(id));
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
            command.add(fullScriptPath.normalize().toAbsolutePath().toString());
            command.add(appDir.resolve("icon.ico").toFile().getAbsolutePath());
            command.add(appName);
        } else {
            // Usage: ./file-assoc.sh "Action" "ID" "Extension" "Name of Application" "Exec-Path" "Icon"
            command.add("add");
            command.add(id);
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
            command.add(appName);
            command.add(fullScriptPath.normalize().toAbsolutePath().toString());
            // TODO: last argument is a 128x128 png image used as icon. if not given uses the bdeploy launcher icon (good enough for now).
        }

        ProcessBuilder b = new ProcessBuilder(command);
        // We are not interested in the output
        b.redirectOutput(Redirect.DISCARD);
        b.redirectError(Redirect.DISCARD);

        try {
            b.start();
        } catch (IOException e) {
            log.error("Failed to create file association for {} ({})", fileAssocExtension, id, e);
        }
    }

    @Override
    public String calculateScriptName(ClientApplicationDto metadata) {
        return ScriptUtils.getFileAssocIdentifier(os, metadata.fileAssocExtension);
    }

    @Override
    protected String getScriptContent() {
        Path launchFile = appDir.resolve(ClientPathHelper.LAUNCH_FILE_NAME).toAbsolutePath();
        if (os == OperatingSystem.WINDOWS) {
            return "@echo off\n"//
                    + "start /d \"" + launcherDir.resolve(ClientPathHelper.LAUNCHER_DIR).toAbsolutePath() + "\" "
                    + ClientPathHelper.WIN_LAUNCHER + " \"" + launchFile + "\" -- %*";
        }
        return "#!/usr/bin/env bash\n"//
                + ClientPathHelper.getNativeLauncher(launcherDir).toAbsolutePath() + " launcher \"--launch=" + launchFile
                + "\" -- \"$@\"";
    }

    @Override
    protected void updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo, boolean override) {
        settings.putFileAssocScriptInfo(name, scriptInfo, override);
    }
}
