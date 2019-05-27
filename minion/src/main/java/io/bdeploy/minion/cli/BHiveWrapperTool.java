package io.bdeploy.minion.cli;

import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.NativeCliTool;

@Help("Wraps to the BHive CLI")
@CliName("bhive")
public class BHiveWrapperTool extends NativeCliTool {

    @Override
    protected void run(String[] args) {
        try {
            new BHiveCli().toolMain(args);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot run BHive CLI", e);
        }
    }

}
