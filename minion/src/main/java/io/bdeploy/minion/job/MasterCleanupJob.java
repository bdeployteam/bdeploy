package io.bdeploy.minion.job;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.jersey.actions.Action;
import io.bdeploy.jersey.actions.ActionExecution;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.plugin.VersionSorterServiceImpl;
import io.bdeploy.ui.cleanup.CleanupHelper;

/**
 * A job that cleans artifacts that are not referenced any more.
 */
@DisallowConcurrentExecution
public class MasterCleanupJob implements Job {

    public static final JobKey JOB_KEY = new JobKey("Cleanup", "Master");

    private static final Logger log = LoggerFactory.getLogger(MasterCleanupJob.class);

    /** Default schedule for cleanup job - once a day at 2:00am. */
    public static final String DEFAULT_CLEANUP_SCHEDULE = "0 0 2 * * ?";

    private static final String DATA_ROOT = MinionRoot.class.getSimpleName();
    private static final String SCHEDULE = "CronSchedule";

    /**
     * Initializes (or updates) the cleanup job to the given schedule.
     *
     * @param minion the minion to cleanup
     * @param cronSchedule the desired schedule or {@code null} to use the default
     */
    public static void create(MinionRoot minion, String cronSchedule) {
        if (cronSchedule == null) {
            cronSchedule = DEFAULT_CLEANUP_SCHEDULE;
        }

        JobBuilder builder = JobBuilder.newJob(MasterCleanupJob.class);
        builder.withIdentity(JOB_KEY).withDescription("Master Cleanup");
        builder.usingJobData(new JobDataMap(Collections.singletonMap(DATA_ROOT, minion)));

        JobDetail job = builder.build();
        Scheduler scheduler = minion.getScheduler();
        Trigger trigger = createCronTrigger(job.getKey(), cronSchedule);
        try {
            Date nextRun = null;
            if (scheduler.checkExists(trigger.getKey())) {
                nextRun = scheduler.rescheduleJob(trigger.getKey(), trigger);
            } else {
                nextRun = scheduler.scheduleJob(job, trigger);
            }
            log.info("Job '{}' scheduled. Trigger '{}'. Next run '{}'.", job.getDescription(), cronSchedule,
                    FormatHelper.formatDate(nextRun));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot schedule cleanup job", e);
        }
    }

    private static Trigger createCronTrigger(JobKey job, String cronSchedule) {
        try {
            return TriggerBuilder.newTrigger().forJob(job).withIdentity("CleanupTrigger", "Master").startNow()
                    .usingJobData(MasterCleanupJob.SCHEDULE, cronSchedule)
                    .withSchedule(CronScheduleBuilder.cronScheduleNonvalidatedExpression(cronSchedule)).build();
        } catch (ParseException e) {
            log.error("Invalid cron schedule: {} using default instead", cronSchedule, e);
            return TriggerBuilder.newTrigger().forJob(job).startNow()
                    .withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_CLEANUP_SCHEDULE)).build();
        }
    }

    @Override
    public void execute(JobExecutionContext context) {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(DATA_ROOT);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
        }

        String cachedSchedule = context.getMergedJobDataMap().getString(SCHEDULE);
        String currentSchedule = mr.getState().cleanupSchedule;
        if (!Objects.equals(cachedSchedule, currentSchedule)) {
            // update schedule of self, but continue with execution
            log.info("Cleanup schedule changed, updating to '{}'", currentSchedule);
            create(mr, currentSchedule);
        }

        performCleanup(mr);
    }

    /**
     * Calculates all {@link InstanceNodeManifest} present on this master, over all {@link InstanceGroupConfiguration}s found in
     * all storage locations. Then uses this information to determine on all nodes what can be deleted there.
     * <p>
     * Does NOT clean anything on the master itself directly (but indirectly as the master is always also a node).
     *
     * @param mr the {@link MinionRoot} of the master minion
     */
    private static void performCleanup(MinionRoot mr) {
        log.info("Performing cleanup on all nodes");

        // no activity reporting on local hives right now (outside request scope, could only use Stream instead).
        try (BHiveRegistry registry = new BHiveRegistry(new ActivityReporter.Null())) {
            mr.getStorageLocations().forEach(s -> registry.scanLocation(s, RollingFileAuditor.getFactory()));

            CleanupHelper ch = new CleanupHelper(null, mr, registry, new SelfMasterProvider(mr.getSelf()),
                    new VersionSorterServiceImpl(mr.getPluginManager(), registry));

            List<CleanupGroup> actions;
            try (ActionHandle h = mr.getActions().start(new Action(Actions.CLEANUP_CALCULATE, null, null, null),
                    ActionExecution.fromSystem())) {
                actions = ch.calculate();
            }

            try (ActionHandle h = mr.getActions().start(new Action(Actions.CLEANUP_PERFORM, null, null, null),
                    ActionExecution.fromSystem())) {
                ch.execute(actions);
            }

            mr.modifyState(s -> s.cleanupLastRun = System.currentTimeMillis());
            log.info("Cleanup finished");
        }
    }

    private static final class SelfMasterProvider implements MasterProvider {

        private final RemoteService self;

        public SelfMasterProvider(RemoteService self) {
            this.self = self;
        }

        @Override
        public RemoteService getControllingMaster(BHive hive, Key assetKey) {
            return self;
        }

        @Override
        public RemoteService getNamedMasterOrSelf(BHive hive, String name) {
            return self;
        }
    }

}
