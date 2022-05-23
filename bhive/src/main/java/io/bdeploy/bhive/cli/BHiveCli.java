/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.cli;

import io.bdeploy.common.cli.ToolBase;

/**
 * Main CLI entry point. Provides commands to execute all known tools.
 */
public class BHiveCli extends ToolBase {

    static final String FS_TOOLS = "Filesystem interaction commands";
    static final String MAINTENANCE_TOOLS = "Analysis and maintenance commands";
    static final String REMOTE_TOOLS = "Remote server interaction commands";
    static final String SERVER_TOOLS = "Server commands";

    public BHiveCli() {
        register(InitTool.class);

        register(ImportTool.class);
        register(ExportTool.class);

        register(ManifestTool.class);
        register(FsckTool.class);
        register(PruneTool.class);
        register(TokenTool.class);
        register(TreeTool.class);
        register(DiscUsageTool.class);

        register(PushTool.class);
        register(FetchTool.class);

        register(ServeTool.class);
    }

    public static void main(String... args) throws Exception {
        new BHiveCli().toolMain(args);
    }

}
