package io.bdeploy.minion.cli;

import io.bdeploy.bhive.cli.TokenTool;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.jersey.cli.LocalLoginTool;

public class MinionServerCli extends ToolBase {

    public MinionServerCli() {
        register(InitTool.class);
        register(ModeTool.class);
        register(CleanupTool.class);
        register(SlaveTool.class);
        register(InteractiveShell.class);
        register(CertUpdateTool.class);
        register(HostnameTool.class);

        // master only tools
        register(InstanceTool.class);
        register(MasterTool.class);
        register(UserTool.class);
        register(StorageTool.class);
        register(RemoteRepoTool.class);

        // tools for local use
        register(LocalLoginTool.class);
        register(ProductTool.class);
        register(TokenTool.class);
        register(BHiveWrapperTool.class);

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
