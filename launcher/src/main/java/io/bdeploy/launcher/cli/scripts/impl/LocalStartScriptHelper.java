package io.bdeploy.launcher.cli.scripts.impl;

import java.util.Map;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.ResolverHelper;
import io.bdeploy.launcher.cli.scripts.LaunchMode;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;

public class LocalStartScriptHelper extends LocalScriptHelper {

    public LocalStartScriptHelper(OperatingSystem os, BHive bhive, LauncherPathProvider lpp) {
        super(os, bhive, lpp, SpecialDirectory.START_SCRIPTS);
    }

    @Override
    public String calculateScriptName(ClientApplicationConfiguration clientCfg) {
        return ScriptUtils.getStartScriptIdentifier(os, clientCfg.appDesc.processControl.startScriptName);
    }

    @Override
    protected String getScriptContent(ClientApplicationConfiguration clientCfg) {
        VariableResolver appSpecificResolver = ResolverHelper.createResolver(lpp, clientCfg);
        ProcessConfiguration pc = clientCfg.appConfig.renderDescriptor(appSpecificResolver);

        Map<String, String> envVars = pc.startEnv;
        envVars.put(LaunchMode.LAUNCH_MODE_ENV_VAR_NAME, LaunchMode.PATH.toString());

        String commandBlock = TemplateHelper.process(pc.start, appSpecificResolver).stream().collect(Collectors.joining(" "));

        StringBuilder sb = new StringBuilder();
        if (os == OperatingSystem.WINDOWS) {
            sb.append("@echo off\n");
            envVars.forEach((k, v) -> sb.append("set \"").append(k).append('=').append(v).append("\"\n"));
            sb.append("call ").append(commandBlock).append(" %*\n");
            sb.append("exit /b %ERRORLEVEL%");
        } else {
            sb.append("#!/usr/bin/env bash\n");
            envVars.forEach((k, v) -> sb.append("export ").append(k).append("=\"").append(v).append("\"\n"));
            sb.append(commandBlock).append(" \"$@\"");
        }
        return sb.toString();
    }

    @Override
    protected boolean updateSettings(LocalClientApplicationSettings settings, String name, ScriptInfo scriptInfo,
            boolean override) {
        return settings.putStartScriptInfo(name, scriptInfo, override);
    }
}
