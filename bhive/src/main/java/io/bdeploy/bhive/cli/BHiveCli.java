/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.cli;

import io.bdeploy.common.cli.ToolBase;

/**
 * Main CLI entry point. Provides commands to execute all known tools.
 */
public class BHiveCli extends ToolBase {

    public BHiveCli() {
        register(ImportTool.class);
        register(ExportTool.class);
        register(ManifestTool.class);
        register(PushTool.class);
        register(FetchTool.class);
        register(FsckTool.class);
        register(PruneTool.class);
        register(ServeTool.class);
        register(TokenTool.class);
        register(TreeTool.class);
    }

    public static void main(String... args) throws Exception {
        new BHiveCli().toolMain(args);
    }

}
