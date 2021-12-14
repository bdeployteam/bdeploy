package io.bdeploy.minion.cli;

import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.jersey.cli.LocalLoginTool;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.ui.cli.TextUIResources;

public class MinionServerCli extends ToolBase {

    public static final String SERVER_TOOLS = "Server commands";
    public static final String MGMT_TOOLS = "Initialization and local configuration management commands";
    public static final String LOCAL_SESSION_TOOLS = "Local session and scripting commands";
    public static final String PRODUCT_TOOLS = "Product management commands";
    public static final String UTIL_TOOLS = "Utility commands";

    public MinionServerCli() {
        // server commands
        register(StartTool.class);
        register(NodeTool.class);

        // init and local configuration
        register(InitTool.class);
        register(CleanupTool.class);
        register(CertUpdateTool.class);
        register(ConfigTool.class);
        register(StorageTool.class);

        // local session and scripting
        register(InteractiveShell.class);
        register(LocalLoginTool.class);

        // product management
        register(ProductTool.class);

        // utilities
        register(BHiveWrapperTool.class);
        register(PayloadTool.class);
        register(VerifySignatureTool.class);

        // remote text UI
        TextUIResources.registerTextUi(this);

        setAuditorFactory(RollingFileAuditor.getFactory());
    }

    public static void main(String... args) throws Exception {
        new MinionServerCli().toolMain(args);
    }

}
