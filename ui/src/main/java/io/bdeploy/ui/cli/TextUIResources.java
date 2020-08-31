package io.bdeploy.ui.cli;

import io.bdeploy.common.cli.ToolBase;

public class TextUIResources {

    public static final String UI_CATEGORY = "Remote server tools";

    public static void registerTextUi(ToolBase tool) {
        tool.register(RemoteInstanceGroupTool.class);
        tool.register(RemoteInstanceTool.class);
        tool.register(RemoteProductTool.class);
        tool.register(RemoteDeploymentTool.class);
        tool.register(RemoteProcessTool.class);
        tool.register(RemoteUserTool.class);
        tool.register(RemoteRepoTool.class);

        tool.register(RemoteMasterTool.class);
        tool.register(RemoteCentralTool.class);
        tool.register(RemotePluginTool.class);
    }

}
