package io.bdeploy.minion.job;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
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

import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.jersey.actions.Action;
import io.bdeploy.jersey.actions.ActionExecution;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.minion.MinionRoot;

/**
 * A job that periodically imports users and groups from LDAP servers
 */
@DisallowConcurrentExecution
public class SyncLdapUserGroupsJob implements Job {

    public static final JobKey JOB_KEY = new JobKey("SyncLdapUserGroupsJob", "Master");

    private static final Logger log = LoggerFactory.getLogger(SyncLdapUserGroupsJob.class);

    public static final String DEFAULT_SYNC_SCHEDULE = "0 0 0 * * ?"; // every midnight

    private static final String MINION = MinionRoot.class.getSimpleName();

    private static final String SCHEDULE = "CronSchedule";

    private static final TriggerKey TRIGGER_KEY = new TriggerKey("SyncLdapUserGroupsTrigger", "Master");

    public static void create(MinionRoot minion, String cronSchedule) {
        if (cronSchedule == null) {
            cronSchedule = DEFAULT_SYNC_SCHEDULE;
        }

        JobDetail job = JobBuilder.newJob(SyncLdapUserGroupsJob.class).withIdentity(JOB_KEY)
                .withDescription("LDAP Synchronization").usingJobData(new JobDataMap(Collections.singletonMap(MINION, minion)))
                .build();

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
            throw new IllegalStateException("Cannot schedule LDAP Synchronization Job", e);
        }
    }

    private static Trigger createCronTrigger(JobDetail job, String cronSchedule) {
        try {
            return TriggerBuilder.newTrigger().forJob(job).withIdentity(TRIGGER_KEY).usingJobData(SCHEDULE, cronSchedule)
                    .withSchedule(CronScheduleBuilder.cronScheduleNonvalidatedExpression(cronSchedule)).build();
        } catch (ParseException e) {
            log.error("Invalid cron schedule: {} using default instead", cronSchedule, e);
            return TriggerBuilder.newTrigger().forJob(job).withIdentity(TRIGGER_KEY)
                    .withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_SYNC_SCHEDULE)).build();
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Sync LDAP users and groups job started");
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(MINION);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
        }

        String cachedSchedule = context.getMergedJobDataMap().getString(SCHEDULE);
        String currentSchedule = mr.getState().ldapSyncSchedule;
        if (currentSchedule != null && !Objects.equals(cachedSchedule, currentSchedule)) {
            // update schedule of self, but continue with execution
            log.info("Sync LDAP schedule changed, updating to '{}'", currentSchedule);
            create(mr, currentSchedule);
        }

        mr.getSettings().auth.ldapSettings.stream().filter(ldap -> ldap.syncEnabled).forEach(ldap -> {
            try (ActionHandle h = mr.getActions().start(new Action(Actions.LDAP_SYNC, null, null, ldap.id),
                    ActionExecution.fromSystem())) {
                String feedback = mr.getUsers().importAccountsLdapServer(ldap);
                log.info(feedback);
            }
        });
        mr.modifyState(s -> s.ldapSyncLastRun = System.currentTimeMillis());
    }
}
