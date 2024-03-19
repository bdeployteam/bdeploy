package io.bdeploy.minion.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.Optional;

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
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.PoolTool.PoolConfig;
import io.bdeploy.minion.job.OrganizePoolJob;

@Help("Manage Pool settings")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("pool")
public class PoolTool extends ConfiguredCliTool<PoolConfig> {

    public @interface PoolConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Sets a default pool directory which will be used when creating new BHives on the server.")
        String defaultPool();

        @Help("Sets the usage threshold for the re-organization job. Only objects references at least this amount of times are pooled.")
        int usageThreshold() default -1;

        @Help("Updates cron schedule for pool re-organization Job")
        String setOrganizeSchedule();

        @Help(value = "Show schedule and last time pool re-organization Job was successfully executed", arg = false)
        boolean showOrganizeInfo() default false;
    }

    public PoolTool() {
        super(PoolConfig.class);
    }

    @Override
    protected RenderableResult run(PoolConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());

        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            if (config.setOrganizeSchedule() != null) {
                return setOriganizeSchedule(mr, config);
            }
            if (config.showOrganizeInfo()) {
                return showOrganizeInfo(mr);
            }
            if (config.defaultPool() != null) {
                return setDefaultPool(mr, config);
            }
            if (config.usageThreshold() != -1) {
                return setThreshold(mr, config);
            }

            return createNoOp();
        }
    }

    private RenderableResult setDefaultPool(MinionRoot mr, PoolConfig config) {
        if (config.defaultPool().isBlank()) {
            // unset!
            mr.modifyState(s -> s.poolDefaultPath = null);
        } else {
            Path path = Paths.get(config.defaultPool());
            if (!PathHelper.exists(path)) {
                PathHelper.mkdirs(path);
            }
            mr.modifyState(s -> s.poolDefaultPath = path);
        }
        return createSuccess();
    }

    private RenderableResult setThreshold(MinionRoot mr, PoolConfig config) {
        mr.modifyState(s -> s.poolUsageThreshold = config.usageThreshold());

        return createSuccess();
    }

    private DataResult setOriganizeSchedule(MinionRoot mr, PoolConfig config) {
        try {
            CronScheduleBuilder.cronScheduleNonvalidatedExpression(config.setOrganizeSchedule());
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid schedule", e);
        }

        mr.modifyState(s -> s.poolOrganizationSchedule = config.setOrganizeSchedule());
        return createSuccess();
    }

    private DataResult showOrganizeInfo(MinionRoot mr) {
        DataResult result = createEmptyResult();
        String schedule = Optional.ofNullable(mr.getState().poolOrganizationSchedule)
                .orElse(OrganizePoolJob.DEFAULT_REORG_SCHEDULE);
        String lastRun = mr.getState().poolOrganizationLastRun == 0 ? "N/A"
                : FormatHelper.format(Instant.ofEpochMilli(mr.getState().poolOrganizationLastRun));
        result.addField("Cron Schedule", schedule);
        result.addField("Last Run", lastRun);
        return result;
    }

}
