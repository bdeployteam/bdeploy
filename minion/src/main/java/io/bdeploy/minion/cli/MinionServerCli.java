package io.bdeploy.minion.cli;

import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.jersey.cli.LocalLoginTool;
import io.bdeploy.ui.cli.TextUIResources;

public class MinionServerCli extends ToolBase {

    public static final String SERVER_TOOLS = "Server commands";
    public static final String MGMT_TOOLS = "Initialization and local configuration management commands";
    public static final String LOCAL_SESSION_TOOLS = "Local session and scripting commands";
    public static final String PRODUCT_TOOLS = "Product management commands";
    public static final String UTIL_TOOLS = "Utility commands";

    public MinionServerCli() {
        // server commands
        register(MasterTool.class);
        register(SlaveTool.class);

        // init and local configuration
        register(InitTool.class);
        register(ModeTool.class);
        register(CleanupTool.class);
        register(CertUpdateTool.class);
        register(HostnameTool.class);
        register(StorageTool.class);

        // local session and scripting
        register(InteractiveShell.class);
        register(LocalLoginTool.class);

        // product management
        register(ProductTool.class);

        // utilities
        register(BHiveWrapperTool.class);

        // remote text UI
        TextUIResources.registerTextUi(this);
    }

    public static void main(String... args) throws Exception {
        new MinionServerCli().toolMain(args);
    }

}
