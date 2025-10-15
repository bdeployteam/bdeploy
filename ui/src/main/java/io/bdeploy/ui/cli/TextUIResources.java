package io.bdeploy.ui.cli;

import io.bdeploy.common.cli.ToolBase;

public class TextUIResources {

    public static final String UI_CATEGORY = "Remote server management commands";

    private TextUIResources() {
    }

    public static void registerTextUi(ToolBase tool) {
        tool.register(RemoteInstanceGroupTool.class);
        tool.register(RemoteInstanceTool.class);
        tool.register(RemoteConfigFilesTool.class);
        tool.register(RemoteProcessConfigTool.class);
        tool.register(RemotePortsTool.class);
        tool.register(RemoteProductTool.class);
        tool.register(RemoteTransferTool.class);
        tool.register(RemoteDeploymentTool.class);
        tool.register(RemoteProcessTool.class);
        tool.register(RemoteUserSelfTool.class);
        tool.register(RemoteUserTool.class);
        tool.register(RemoteUserGroupTool.class);
        tool.register(RemoteRepoTool.class);
        tool.register(RemoteRepoSoftwareTool.class);
        tool.register(RemoteSystemTool.class);

        tool.register(RemoteMasterTool.class);
        tool.register(RemoteCentralTool.class);
        tool.register(RemotePluginTool.class);
        tool.register(RemoteDataFilesTool.class);
        tool.register(RemoteProductValidationTool.class);
        tool.register(RemoteReportTool.class);
    }
}
