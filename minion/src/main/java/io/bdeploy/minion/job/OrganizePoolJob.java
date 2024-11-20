package io.bdeploy.minion.job;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHivePoolOrganizer;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.minion.MinionRoot;

/**
 * A job that periodically imports users and groups from LDAP servers
 */
@DisallowConcurrentExecution
public class OrganizePoolJob implements Job {

    public static final JobKey JOB_KEY = new JobKey("PoolReorgJob", "Master");

    private static final Logger log = LoggerFactory.getLogger(OrganizePoolJob.class);

    public static final String DEFAULT_REORG_SCHEDULE = "0 0 4 * * ?"; // every day at 4am.

    private static final String MINION = MinionRoot.class.getSimpleName();
    private static final String REGISTRY = BHiveRegistry.class.getSimpleName();

    private static final String SCHEDULE = "CronSchedule";

    private static final TriggerKey TRIGGER_KEY = new TriggerKey("PoolReorgTrigger", "Master");

    public static void create(MinionRoot minion, BHiveRegistry registry, String cronSchedule) {
        if (cronSchedule == null) {
            cronSchedule = DEFAULT_REORG_SCHEDULE;
        }

        JobDetail job = JobBuilder.newJob(OrganizePoolJob.class).withIdentity(JOB_KEY).withDescription("Pool Re-organization")
                .usingJobData(new JobDataMap(Map.of(MINION, minion, REGISTRY, registry))).build();

        Scheduler scheduler = minion.getScheduler();
        Trigger trigger = createCronTrigger(job, cronSchedule);
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
            throw new IllegalStateException("Cannot schedule " + job.getDescription(), e);
        }
    }

    private static Trigger createCronTrigger(JobDetail job, String cronSchedule) {
        try {
            return TriggerBuilder.newTrigger().forJob(job).withIdentity(TRIGGER_KEY).usingJobData(SCHEDULE, cronSchedule)
                    .withSchedule(CronScheduleBuilder.cronScheduleNonvalidatedExpression(cronSchedule)
                            .withMisfireHandlingInstructionDoNothing()).build();
        } catch (ParseException e) {
            log.error("Invalid cron schedule: {} using default instead", cronSchedule, e);
            return TriggerBuilder.newTrigger().forJob(job).withIdentity(TRIGGER_KEY)
                    .withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_REORG_SCHEDULE)).build();
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(MINION);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
        }
        BHiveRegistry registry = (BHiveRegistry) context.getMergedJobDataMap().get(REGISTRY);
        if (registry == null) {
            throw new IllegalStateException("No BHive registry set");
        }

        String cachedSchedule = context.getMergedJobDataMap().getString(SCHEDULE);
        String currentSchedule = mr.getState().poolOrganizationSchedule;
        if (currentSchedule != null && !Objects.equals(cachedSchedule, currentSchedule)) {
            // update schedule of self, but continue with execution
            log.info("Pool re-organization schedule changed, updating to '{}'", currentSchedule);
            create(mr, registry, currentSchedule);
        }

        BHivePoolOrganizer.reorganizeAll(registry, mr.getState().poolUsageThreshold, mr.getActions());

        mr.modifyState(s -> s.poolOrganizationLastRun = System.currentTimeMillis());
    }
}
