package io.bdeploy.launcher.cli.scripts.impl;

import java.nio.file.Path;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.scripts.LaunchMode;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;

public class LocalStartScriptHelper extends LocalScriptHelper {

    public LocalStartScriptHelper(OperatingSystem os, Auditor auditor, LauncherPathProvider lpp, SpecialDirectory scriptDir) {
        super(os, auditor, lpp, scriptDir);
    }

    @Override
    public String calculateScriptName(ClientApplicationDto metadata) {
        return ScriptUtils.getStartScriptIdentifier(os, metadata.startScriptName);
    }

    @Override
    protected String getScriptContent() {
        String envVar = LaunchMode.LAUNCH_MODE_ENV_VAR_NAME + "=" + LaunchMode.PATH;
        Path scriptLauncher = ClientPathHelper.getScriptLauncher(lpp.get(SpecialDirectory.HOME));
        Path launchFile = lpp.get(SpecialDirectory.APP).resolve(ClientPathHelper.LAUNCH_FILE_NAME);
        if (os == OperatingSystem.WINDOWS) {
            return "@echo off\n"//
                    + "set " + envVar + "\n"//
                    + '"' + scriptLauncher + "\" \"launcher\" \"--launch=" + launchFile + "\" --noSplash -- %*";
        }
        return "#!/usr/bin/env bash\n"//
                + "export " + envVar + "\n"//
                + scriptLauncher + " launcher \"--launch=" + launchFile + "\" --noSplash -- \"$@\"";
    }

    @Override
    protected void updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo, boolean override) {
        settings.putStartScriptInfo(name, scriptInfo, override);
    }
}
