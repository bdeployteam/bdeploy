package io.bdeploy.launcher.cli.scripts.impl;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.scripts.LaunchMode;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;

public class LocalFileAssocScriptHelper extends LocalScriptHelper {

    private static final Logger log = LoggerFactory.getLogger(LocalFileAssocScriptHelper.class);

    public LocalFileAssocScriptHelper(OperatingSystem os, BHive bhive, LauncherPathProvider lpp) {
        super(os, bhive, lpp, SpecialDirectory.FILE_ASSOC_SCRIPTS);
    }

    @Override
    protected void afterUpdateHook(ClientApplicationConfiguration clientCfg, Path fullScriptPath) {
        ApplicationConfiguration appConfig = clientCfg.appConfig;
        String id = appConfig.id;
        if (id == null) {
            log.error("Failed to create file association because no ID was given.");
            return;
        }
        String fileAssocExtension = clientCfg.appDesc.processControl.fileAssocExtension;
        if (fileAssocExtension == null) {
            log.error("Failed to create file association for {} because no file association extension was given.", id);
            return;
        }
        String appName = appConfig.name;
        if (appName == null) {
            log.error("Failed to create file association for {} files ({})", fileAssocExtension, id);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(ClientPathHelper.getNativeFileAssocTool(lpp).toString());
        if (os == OperatingSystem.WINDOWS) {
            command.add("/InstallApplication");
            command.add(ScriptUtils.getBDeployFileAssocId(id));
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
            command.add(fullScriptPath.toString());
            command.add(lpp.get(SpecialDirectory.APP).resolve("icon.ico").toString());
            command.add(appName);
        } else {
            // Usage: ./file-assoc.sh "Action" "ID" "Extension" "Name of Application" "Exec-Path" "Icon"
            command.add("add");
            command.add(id);
            command.add(ScriptUtils.getFullFileExtension(fileAssocExtension));
            command.add(appName);
            command.add(fullScriptPath.toString());
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
    public String calculateScriptName(ClientApplicationConfiguration clientCfg) {
        return ScriptUtils.getFileAssocIdentifier(os, clientCfg.appDesc.processControl.fileAssocExtension);
    }

    @Override
    protected String getScriptContent(ClientApplicationConfiguration clientCfg) {
        String envVar = LaunchMode.LAUNCH_MODE_ENV_VAR_NAME + "=" + LaunchMode.ASSOCIATION;
        Path launchFile = lpp.get(SpecialDirectory.APP).resolve(ClientPathHelper.LAUNCH_FILE_NAME);
        if (os == OperatingSystem.WINDOWS) {
            return "@echo off\n"//
                    + "set " + envVar + "\n"//
                    + "start /d \"" + lpp.get(SpecialDirectory.LAUNCHER) + "\" " + ClientPathHelper.WIN_LAUNCHER + " \""
                    + launchFile + "\" -- %*";
        }
        return "#!/usr/bin/env bash\n"//
                + "export " + envVar + "\n"//
                + ClientPathHelper.getNativeLauncher(lpp) + " launcher \"--launch=" + launchFile + "\" -- \"$@\"";
    }

    @Override
    protected boolean updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo,
            boolean override) {
        return settings.putFileAssocScriptInfo(name, scriptInfo, override);
    }
}
