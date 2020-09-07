package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.ConfigurationValueMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.Configuration.ValueMapping;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.ModeTool.ModeConfig;
import io.bdeploy.ui.api.MinionMode;

@Help("Sets the mode of a minion root directory")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("set-mode")
public class ModeTool extends ConfiguredCliTool<ModeConfig> {

    public @interface ModeConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("The target mode for the server [MANAGED,STANDALONE]. A MANAGED server can only work with a central counterpart.")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        MinionMode mode();
    }

    public ModeTool() {
        super(ModeConfig.class);
    }

    @Override
    protected RenderableResult run(ModeConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.mode(), "Missing --mode");

        Path root = Paths.get(config.root());

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            mr.getAuditor()
                    .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("set-mode").build());

            MinionMode oldMode = mr.getMode();
            MinionMode newMode = config.mode();

            if (oldMode == MinionMode.CENTRAL || oldMode == MinionMode.SLAVE) {
                throw new UnsupportedOperationException("Cannot convert " + oldMode + " root to anything else");
            }

            if (newMode == MinionMode.CENTRAL || newMode == MinionMode.SLAVE) {
                throw new UnsupportedOperationException("Cannot convert root to " + oldMode + ".");
            }

            mr.modifyState(s -> s.mode = config.mode());

            return createSuccess().addField("Old Mode", oldMode).addField("New Mode", newMode);
        }
    }

}
