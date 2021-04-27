package io.bdeploy.minion.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;

@Help("Testing for Gradle classpath issues")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("testGradle")
public class GradleTestTool extends CliTool {

    private static final Logger log = LoggerFactory.getLogger(GradleTestTool.class);

    @Override
    public RenderableResult run() {
        log.info("GRADLE Modular Classpath Test Tool");

        return createSuccess();
    }

    public static void main(String[] args) throws Exception {
        new MinionServerCli().toolMain("testGradle");
    }

}
