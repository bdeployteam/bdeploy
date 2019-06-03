package io.bdeploy.minion.cleanup;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;
import io.bdeploy.minion.MinionRoot;

public class MasterCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(MasterCleanupJob.class);

    public static final String DATA_ROOT = MinionRoot.class.getSimpleName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        MinionRoot mr = (MinionRoot) context.getMergedJobDataMap().get(DATA_ROOT);
        if (mr == null) {
            throw new IllegalStateException("No minion root set");
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

        SortedSet<Key> allUniqueKeysToKeep = new TreeSet<>();

        // no activity reporting on local hives right now (outside request scope, could only use Stream instead).
        try (BHiveRegistry registry = new BHiveRegistry(new ActivityReporter.Null())) {
            mr.getStorageLocations().forEach(registry::scanLocation);

            for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
                BHive toCheck = entry.getValue();
                InstanceGroupConfiguration ig = new InstanceGroupManifest(toCheck).read();
                if (ig == null) {
                    // not an instance group, skip.
                    // this is either the default hive (slave hive), or a software repository.
                }
                log.info("Gathering information for instance group {} ({})", ig.name, ig.description);

                // instance manifests
                SortedSet<Key> imfs = InstanceManifest.scan(toCheck, false);

                // instance node manifests
                SortedSet<Key> inmfs = imfs.stream().map(key -> InstanceManifest.of(toCheck, key))
                        .flatMap(im -> im.getInstanceNodeManifests().values().stream())
                        .collect(Collectors.toCollection(TreeSet::new));

                log.info("Collected {} node manifests", inmfs.size());

                allUniqueKeysToKeep.addAll(inmfs);
            }

            for (Map.Entry<String, RemoteService> slave : mr.getState().minions.entrySet()) {
                log.info("Cleaning on {}, using {} anchors.", slave.getKey(), allUniqueKeysToKeep.size());

                SlaveCleanupResource scr = ResourceProvider.getResource(slave.getValue(), SlaveCleanupResource.class);
                try {
                    scr.cleanup(allUniqueKeysToKeep, true);
                } catch (Exception e) {
                    log.warn("Cannot perform cleanup on slave {}", slave.getKey());
                    if (log.isDebugEnabled()) {
                        log.debug("Error details", e);
                    }
                }
            }

            mr.modifyState(s -> s.cleanupLastRun = System.currentTimeMillis());
            log.info("Cleanup finished");
        }
    }

}
