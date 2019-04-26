package io.bdeploy.minion.cli;

import io.bdeploy.bhive.cli.TokenTool;
import io.bdeploy.common.cli.ToolBase;

public class MinionServerCli extends ToolBase {

    public MinionServerCli() {
        register(InitTool.class);
        register(SlaveTool.class);

        // master only tools
        register(TemplateTool.class);
        register(MasterTool.class);
        register(UserTool.class);
        register(StorageTool.class);
        register(RepoTool.class);

        // tools for local use
        register(ProductTool.class);
        register(TokenTool.class);

        // remote client
        register(RemoteMasterTool.class);
        register(RemoteDeploymentTool.class);
        register(RemoteProcessTool.class);
        register(RemoteInstanceGroupTool.class);
    }

    public static void main(String... args) throws Exception {
        new MinionServerCli().toolMain(args);
    }

}
