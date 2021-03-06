package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.InitTool.InitConfig;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.jersey.audit.AuditRecord;

/**
 * Initializes a previously empty directory as BHive.
 */
@Help("Initializes an empty BHive")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("init")
public class InitTool extends ConfiguredCliTool<InitConfig> {

    public @interface InitConfig {

        @Help("The directory to initialize as BHive")
        @EnvironmentFallback("BHIVE")
        @Validator({ NonExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();
    }

    public InitTool() {
        super(InitConfig.class);
    }

    @Override
    protected RenderableResult run(InitConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        Path root = Paths.get(config.hive());
        try (BHive hive = new BHive(root.toUri(), getActivityReporter())) {
            hive.getAuditor()
                    .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("init").build());
        }

        return createSuccess();
    }

}