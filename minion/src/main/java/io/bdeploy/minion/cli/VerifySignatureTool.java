package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.minion.cli.VerifySignatureTool.VerifyConfig;
import io.bdeploy.ui.utils.WindowsExecutableUtils;

/**
 * Verifies that a given executable is properly signed, e.g. for pre-release checks.
 */
@Help("Verifies that the given binary has a valid signature")
@ToolCategory(MinionServerCli.UTIL_TOOLS)
@CliName("verify-signature")
public class VerifySignatureTool extends ConfiguredCliTool<VerifyConfig> {

    public @interface VerifyConfig {

        @Help("Signed PE/COFF executable to verify.")
        @Validator(ExistingPathValidator.class)
        String executable();

    }

    public VerifySignatureTool() {
        super(VerifyConfig.class);
    }

    @Override
    protected RenderableResult run(VerifyConfig config) {
        helpAndFailIfMissing(config.executable(), "Missing --executable");

        try {
            Path exe = Paths.get(config.executable());
            WindowsExecutableUtils.verify(exe);
        } catch (IOException e) {
            return createResultWithErrorMessage("Cannot verify executable").setException(e);
        }

        return createSuccess();
    }

}
