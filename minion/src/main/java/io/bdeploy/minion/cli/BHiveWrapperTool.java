package io.bdeploy.minion.cli;

import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.NativeCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;

@Help("Wraps to the BHive CLI")
@ToolCategory(MinionServerCli.UTIL_TOOLS)
@CliName("bhive")
public class BHiveWrapperTool extends NativeCliTool {

    @Override
    protected RenderableResult run(String[] args) {
        try {
            BHiveCli cli = new BHiveCli();
            cli.setAuditorFactory(getAuditorFactory());
            cli.toolMain(args);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot run BHive CLI", e);
        }
        return null; // BHive CLI handles this itself.
    }

}
