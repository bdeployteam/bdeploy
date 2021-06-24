package io.bdeploy.minion.job;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionMonitoringDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;

/**
 * A job that queries and stores the server load every minute.
 */
public class NodeMonitorJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(NodeMonitorJob.class);

    /** Schedule for job */
    private static final String JOB_SCHEDULE = "0 * * * * ?";

    private static final String DATA_ROOT = "DataRoot";

    public static void create(MinionRoot minion) {
        JobBuilder jBuilder = JobBuilder.newJob(NodeMonitorJob.class);
        jBuilder.withIdentity("Monitoring", "Master").withDescription("Collect Server Load Average");
        jBuilder.usingJobData(new JobDataMap(Collections.singletonMap(DATA_ROOT, minion)));

        TriggerBuilder<Trigger> tBuilder = TriggerBuilder.newTrigger().withIdentity("Monitoring", "Master");
        tBuilder.withSchedule(CronScheduleBuilder.cronSchedule(JOB_SCHEDULE));

        try {
            JobDetail job = jBuilder.build();
            Trigger trigger = tBuilder.build();
            Date nextRun = minion.getScheduler().scheduleJob(job, trigger);
            log.info("Job '{}' scheduled. Trigger '{}'. Next run '{}'.", job.getDescription(), JOB_SCHEDULE,
                    FormatHelper.format(nextRun));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot schedule job", e);
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(DATA_ROOT);

        if (mr.getMode() != MinionMode.CENTRAL) {
            MinionManifest minionManifest = new MinionManifest(mr.getHive());
            MinionConfiguration configuration = minionManifest.read();
            for (Map.Entry<String, MinionDto> node : configuration.entrySet()) {
                log.debug("get monitoring information from node {}", node.getKey());

                RemoteService remote = node.getValue().remote;
                MinionStatusResource msr = ResourceProvider.getVersionedResource(remote, MinionStatusResource.class, null);
                try {
                    MinionMonitoringDto monitoringDto = msr.getMonitoring(!node.getValue().master);
                    node.getValue().monitoring = monitoringDto;
                } catch (Exception e) {
                    log.warn("Cannot get monitoring information on minion {} ({})", node.getKey(), e.getClass().getName());
                    if (log.isDebugEnabled()) {
                        log.debug("Error details", e);
                    }
                }
            }
            minionManifest.update(configuration);
            mr.getHive().execute(new ManifestDeleteOldByIdOperation().setToDelete(minionManifest.getKey().getName()));
        }
    }
}
