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
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.LdapTool.LdapConfig;
import io.bdeploy.minion.job.SyncLdapUserGroupsJob;

@Help("Manage LDAP settings")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("ldap")
public class LdapTool extends ConfiguredCliTool<LdapConfig> {

    public @interface LdapConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Updates cron schedule for LDAP Synchronization Job")
        String setSyncSchedule();

        @Help(value = "Show schedule and last time LDAP Synchronization Job was successfully executed", arg = false)
        boolean showSyncInfo();

    }

    public LdapTool() {
        super(LdapConfig.class);
    }

    @Override
    protected RenderableResult run(LdapConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());

        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            if (config.setSyncSchedule() != null) {
                return setLdapSyncSchedule(mr, config);
            } else if (config.showSyncInfo()) {
                return showLdapSyncInfo(mr);
            } else {
                return createNoOp();
            }
        }
    }

    private DataResult setLdapSyncSchedule(MinionRoot mr, LdapConfig config) {
        try {
            CronScheduleBuilder.cronScheduleNonvalidatedExpression(config.setSyncSchedule());
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid schedule", e);
        }

        mr.modifyState(s -> s.ldapSyncSchedule = config.setSyncSchedule());
        return createSuccess();
    }

    private DataResult showLdapSyncInfo(MinionRoot mr) {
        DataResult result = createEmptyResult();
        String schedule = Optional.ofNullable(mr.getState().ldapSyncSchedule).orElse(SyncLdapUserGroupsJob.DEFAULT_SYNC_SCHEDULE);
        String lastRun = mr.getState().ldapSyncLastRun == 0 ? "N/A"
                : FormatHelper.formatInstant(Instant.ofEpochMilli(mr.getState().ldapSyncLastRun));
        result.addField("Cron Schedule", schedule);
        result.addField("Last Run", lastRun);
        return result;
    }

}
