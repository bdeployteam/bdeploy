package io.bdeploy.launcher.cli.scripts.impl;

import java.nio.file.Path;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;

public class LocalStartScriptHelper extends LocalScriptHelper {

    public LocalStartScriptHelper(OperatingSystem os, Auditor auditor, Path launcherDir, Path appDir, Path scriptDir) {
        super(os, auditor, launcherDir, appDir, scriptDir);
    }

    @Override
    public String calculateScriptName(ClientApplicationDto metadata) {
        return ScriptUtils.getStartScriptIdentifier(os, metadata.startScriptName);
    }

    @Override
    protected String getScriptContent() {
        if (os == OperatingSystem.WINDOWS) {
            return "@echo off\n"//
                    + '"' + ClientPathHelper.getScriptLauncher(launcherDir).toAbsolutePath() + "\" \"launcher\" \"--launch="
                    + appDir.resolve(ClientPathHelper.LAUNCH_FILE_NAME).toAbsolutePath() + "\" --noSplash -- %*";
        }
        return "#!/usr/bin/env bash\n" //
                + ClientPathHelper.getScriptLauncher(launcherDir).toAbsolutePath() + " launcher \"--launch="
                + appDir.resolve(ClientPathHelper.LAUNCH_FILE_NAME).toAbsolutePath() + "\" --noSplash -- \"$@\"";
    }

    @Override
    protected void updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo, boolean override) {
        settings.putStartScriptInfo(name, scriptInfo, override);
    }
}
