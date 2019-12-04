package io.bdeploy.minion.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.quartz.CronScheduleBuilder;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.CleanupTool.CleanupConfig;
import io.bdeploy.minion.job.MasterCleanupJob;
import io.bdeploy.ui.api.MinionMode;

@Help("Manage cleanup settings")
@CliName("cleanup")
public class CleanupTool extends ConfiguredCliTool<CleanupConfig> {

    public @interface CleanupConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("Set/update the schedule ('cron' syntax) for the master cleanup job, default: '"
                + MasterCleanupJob.DEFAULT_CLEANUP_SCHEDULE + "'")
        String setSchedule();
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public CleanupTool() {
        super(CleanupConfig.class);
    }

    @Override
    protected void run(CleanupConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());
        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, MinionMode.TOOL, getActivityReporter())) {
            if (config.setSchedule() != null) {
                try {
                    CronScheduleBuilder.cronScheduleNonvalidatedExpression(config.setSchedule());
                } catch (ParseException e) {
                    throw new IllegalStateException("Invalid schedule", e);
                }

                mr.modifyState(s -> s.cleanupSchedule = config.setSchedule());
            } else {
                out().println("Cleanup scheduled at: '" + mr.getState().cleanupSchedule + "', last run at "
                        + FORMATTER.format(Instant.ofEpochMilli(mr.getState().cleanupLastRun)));
            }
        }
    }

}
