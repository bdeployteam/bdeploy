package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UnitHelper;

/**
 * Cleans software from the hive which is not used any more. The {@linkplain ClientSoftwareManifest} is used in
 * order to determine which software is used and which one not. Applications listed in the hive which are not referenced by any
 * manifest are removed from the hive and from the pool. Only the latest version of the manifest is taken into account.
 */
public class ClientAppCleanup {

    private static final Logger log = LoggerFactory.getLogger(ClientAppCleanup.class);

    private final BHive hive;
    private final Path appPool;

    /**
     * Creates a new cleanup instance using the given hive
     */
    public ClientAppCleanup(BHive hive, Path appPool) {
        this.hive = hive;
        this.appPool = appPool;
    }

    /**
     * Removes software that is not used anymore.
     */
    public void run() {
        // Collect all required software
        ClientSoftwareManifest mf = new ClientSoftwareManifest(hive);
        Set<Key> requiredApps = mf.getRequiredKeys();

        // Collect all available software in the hive
        Set<Key> availableApps = getAvailableApps();
        if (availableApps.isEmpty()) {
            log.info("Cleanup not required. No software is installed.");
            return;
        }

        // Remove all the software that is still required
        availableApps.removeAll(requiredApps);
        if (availableApps.isEmpty()) {
            log.info("Cleanup not required. All pooled software is still in-use.");
            return;
        }

        // Cleanup hive and pool
        log.info("Removing stale pooled applications that are not used any more...");
        for (Manifest.Key key : availableApps) {
            log.info("Deleting {}", key);

            hive.execute(new ManifestDeleteOperation().setToDelete(key));

            Path pooledPath = appPool.resolve(key.directoryFriendlyName());
            if (pooledPath.toFile().exists()) {
                PathHelper.deleteRecursive(pooledPath);
            }
        }

        // Cleanup stale elements from the hive
        SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());
        long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();
        log.info("Cleanup successfully done. Removed {} objects ({}).", result.size(), UnitHelper.formatFileSize(sum));
    }

    /**
     * Returns a list of all applications available in the hive
     */
    private Set<Key> getAvailableApps() {
        SortedSet<Key> allKeys = hive.execute(new ManifestListOperation());
        return allKeys.stream().filter(ClientAppCleanup::isApp).collect(Collectors.toSet());
    }

    /** Returns whether or not the given manifest refers to an app */
    private static boolean isApp(Key key) {
        return !key.getName().startsWith("meta/");
    }

}
