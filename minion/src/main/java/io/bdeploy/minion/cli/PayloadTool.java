package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.minion.cli.PayloadTool.PayloadConfig;
import io.bdeploy.ui.utils.WindowsExecutableUtils;

/**
 * Manages PE executable payloads.
 */
@Help("Attach a payload to a signed PE/COFF executable.")
@ToolCategory(MinionServerCli.UTIL_TOOLS)
@CliName("payload")
public class PayloadTool extends ConfiguredCliTool<PayloadConfig> {

    public @interface PayloadConfig {

        @Help("Signed PE/COFF executable to modify.")
        @Validator(ExistingPathValidator.class)
        String executable();

        @Help("The path to the payload to attach.")
        @Validator(ExistingPathValidator.class)
        String payload();

    }

    public PayloadTool() {
        super(PayloadConfig.class);
    }

    @Override
    protected RenderableResult run(PayloadConfig config) {
        helpAndFailIfMissing(config.executable(), "Missing --executable");
        helpAndFailIfMissing(config.payload(), "Missing --payload");

        try {
            Path exe = Paths.get(config.executable());
            Path payload = Paths.get(config.payload());
            WindowsExecutableUtils.embed(exe, Files.readAllBytes(payload));
            return createSuccess();
        } catch (IOException e) {
            return createResultWithMessage("Cannot attach payload").setException(e);
        }

    }

}
