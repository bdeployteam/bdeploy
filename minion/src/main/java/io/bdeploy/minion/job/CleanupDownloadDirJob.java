package io.bdeploy.minion.job;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.minion.MinionRoot;

/**
 * A job that cleans top-level files and directories that are older than one hour.
 */
@DisallowConcurrentExecution
public class CleanupDownloadDirJob implements Job {

    public static final JobKey JOB_KEY = new JobKey("CleanupDownloadDirJob", "Master");

    private static final Logger log = LoggerFactory.getLogger(CleanupDownloadDirJob.class);

    /** Default schedule for cleanup job */
    private static final String DEFAULT_CLEANUP_SCHEDULE = "0 */10 * * * ?";

    private static final String DOWNLOAD_DIR = "downloadDir";

    private static final String MINION = MinionRoot.class.getSimpleName();

    /**
     * Initializes the job that cleans the download directory
     *
     * @param scheduler the quartz scheduler
     * @param downloadDir the directory to clean
     */
    public static void create(Scheduler scheduler, Path downloadDir, MinionRoot minion) {
        JobBuilder jBuilder = JobBuilder.newJob(CleanupDownloadDirJob.class);
        jBuilder.withIdentity(JOB_KEY).withDescription("Cleanup Download Directory");
        jBuilder.usingJobData(new JobDataMap(Collections.singletonMap(DOWNLOAD_DIR, downloadDir)));
        jBuilder.usingJobData(new JobDataMap(Map.of(DOWNLOAD_DIR, downloadDir, MINION, minion)));

        TriggerBuilder<Trigger> tBuilder = TriggerBuilder.newTrigger().withIdentity("CleanupDownloadDirTrigger", "Master");
        tBuilder.withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_CLEANUP_SCHEDULE));

        try {
            JobDetail job = jBuilder.build();
            Trigger trigger = tBuilder.build();
            Date nextRun = scheduler.scheduleJob(job, trigger);
            log.info("Job '{}' scheduled. Trigger '{}'. Next run '{}'.", job.getDescription(), DEFAULT_CLEANUP_SCHEDULE,
                    FormatHelper.formatDate(nextRun));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot schedule job", e);
        }
    }

    @Override
    public void execute(JobExecutionContext context) {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(MINION);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
        }
        Path downloadDir = (Path) context.getMergedJobDataMap().get(DOWNLOAD_DIR);
        try (Stream<Path> paths = Files.list(downloadDir)) {
            paths.forEach(CleanupDownloadDirJob::checkAndDelete);
        } catch (IOException ioe) {
            log.error("Failed to cleanup download dir", ioe);
        }
        mr.modifyState(s -> s.cleanupDownloadsDirLastRun = System.currentTimeMillis());
    }

    /**
     * Deletes this file or folder if it is older than one hour.
     *
     * @param path
     *            the path to delete
     */
    private static void checkAndDelete(Path path) {
        long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        File file = path.toFile();
        if (file.lastModified() > oneHourAgo) {
            return;
        }
        PathHelper.deleteRecursiveRetry(path);
    }

}
