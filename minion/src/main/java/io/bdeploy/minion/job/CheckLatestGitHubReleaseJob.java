package io.bdeploy.minion.job;

import java.util.Collections;
import java.util.Date;

import org.quartz.CronScheduleBuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.Version;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.minion.MinionRoot;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;

/**
 * A job that checks the latest BDeploy release version on GitHub
 */
public class CheckLatestGitHubReleaseJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CheckLatestGitHubReleaseJob.class);

    private static final String DEFAULT_CHECK_SCHEDULE = "0 0 */6 * * ?";

    private static final String MINION = MinionRoot.class.getSimpleName();

    public static void create(MinionRoot minion) {
        try {
            Scheduler scheduler = minion.getScheduler();

            JobKey jobKey = new JobKey("GitHubRelease", "Master");

            JobDetail job = JobBuilder.newJob(CheckLatestGitHubReleaseJob.class).withIdentity(jobKey)
                    .withDescription("Check Latest GitHub Release Job")
                    .usingJobData(new JobDataMap(Collections.singletonMap(MINION, minion))).build();

            Trigger trigger = TriggerBuilder.newTrigger().withIdentity("GitHubChecker", "Master")
                    .withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_CHECK_SCHEDULE)).build();

            Date nextRun = scheduler.scheduleJob(job, trigger);

            log.info("Job '{}' scheduled. Trigger '{}'. Next run '{}'.", job.getDescription(), DEFAULT_CHECK_SCHEDULE,
                    FormatHelper.format(nextRun));

            // trigger job immediately
            scheduler.triggerJob(jobKey);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot schedule job", e);
        }

    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("GitHub check latest release job started");

        try (Client client = ClientBuilder.newClient()) {
            LatestGitHubReleaseDto resp = client.target("https://api.github.com/repos/bdeployteam/bdeploy/releases/latest")
                    .request(MediaType.APPLICATION_JSON).get(LatestGitHubReleaseDto.class);
            String v = resp.tagName.startsWith("v") ? resp.tagName.substring(1) : resp.tagName;
            Version latestRelease = VersionHelper.parse(v);
            MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(MINION);
            mr.setLatestGitHubReleaseVersion(latestRelease);
            log.info("Latest GitHub Release Version {}", latestRelease);
        } catch (Exception e) {
            log.warn("Failed to check latest release version: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Error", e);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LatestGitHubReleaseDto {

        @JsonProperty("tag_name")
        public String tagName;

    }
}
