package io.bdeploy.minion.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;

import org.quartz.CronScheduleBuilder;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.CleanupTool.CleanupConfig;
import io.bdeploy.minion.job.MasterCleanupJob;

@Help("Manage cleanup settings")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("cleanup")
public class CleanupTool extends ConfiguredCliTool<CleanupConfig> {

    public @interface CleanupConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Set/update the schedule ('cron' syntax) for the master cleanup job, default: '"
                + MasterCleanupJob.DEFAULT_CLEANUP_SCHEDULE + "'")
        String setSchedule();
    }

    public CleanupTool() {
        super(CleanupConfig.class);
    }

    @Override
    protected RenderableResult run(CleanupConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());
        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            if (config.setSchedule() != null) {
                try {
                    CronScheduleBuilder.cronScheduleNonvalidatedExpression(config.setSchedule());
                } catch (ParseException e) {
                    throw new IllegalStateException("Invalid schedule", e);
                }

                mr.modifyState(s -> s.cleanupSchedule = config.setSchedule());
                return createSuccess();
            } else {
                DataResult result = createEmptyResult();
                result.addField("Schedule", mr.getState().cleanupSchedule);
                result.addField("Last Run", FormatHelper.formatInstant(Instant.ofEpochMilli(mr.getState().cleanupLastRun)));
                return result;
            }
        }
    }

}
