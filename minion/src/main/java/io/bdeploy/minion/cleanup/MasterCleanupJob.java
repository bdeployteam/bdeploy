package io.bdeploy.minion.cleanup;

import java.util.Objects;
import java.util.SortedSet;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.interfaces.cleanup.CleanupHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.minion.MinionRoot;

public class MasterCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(MasterCleanupJob.class);

    public static final String DATA_ROOT = MinionRoot.class.getSimpleName();
    public static final String SCHEDULE = "CronSchedule";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(DATA_ROOT);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
        }

        String cachedSchedule = context.getMergedJobDataMap().getString(SCHEDULE);
        String currentSchedule = mr.getState().cleanupSchedule;
        if (!Objects.equals(cachedSchedule, currentSchedule)) {
            // update schedule of self, but continue with execution
            log.info("Cleanup schedule changed, updating to '{}'", currentSchedule);
            mr.initCleanupJob(currentSchedule);
        }

        performCleanup(mr);
    }

    /**
     * Calculates all {@link InstanceNodeManifest} present on this master, over all {@link InstanceGroupConfiguration}s found in
     * all storage locations. Then uses this information to determine on all slaves what can be deleted there.
     * <p>
     * Does NOT clean anything on the master itself directly (but indirectly as the master is always also a slave).
     *
     * @param mr the {@link MinionRoot} of the master minion
     */
    public void performCleanup(MinionRoot mr) {
        log.info("Performing cleanup on all slaves");

        // no activity reporting on local hives right now (outside request scope, could only use Stream instead).
        try (BHiveRegistry registry = new BHiveRegistry(new ActivityReporter.Null())) {
            mr.getStorageLocations().forEach(registry::scanLocation);

            SortedSet<Key> allUniqueKeysToKeep = CleanupHelper.findAllUniqueKeys(registry);
            CleanupHelper.cleanAllMinions(mr.getMinions(), allUniqueKeysToKeep, true);

            mr.modifyState(s -> s.cleanupLastRun = System.currentTimeMillis());
            log.info("Cleanup finished");
        }
    }

}
