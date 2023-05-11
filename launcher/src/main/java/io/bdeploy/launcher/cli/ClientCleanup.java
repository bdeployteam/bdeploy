package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;

/**
 * Cleans software from the hive which is not used any more. The {@linkplain ClientSoftwareManifest} is used in
 * order to determine which software is used and which one not. Applications listed in the hive which are not referenced by any
 * manifest are removed from the hive and from the pool. Only the latest version of the manifest is taken into account.
 */
public class ClientCleanup {

    private static final Logger log = LoggerFactory.getLogger(ClientCleanup.class);

    private final BHive hive;
    private final Path rootDir;
    private final Path appsDir;
    private final Path poolDir;

    /**
     * Creates a new cleanup instance using the given hive
     */
    public ClientCleanup(BHive hive, Path rootDir, Path appsDir, Path poolDir) {
        this.hive = hive;
        this.rootDir = rootDir;
        this.appsDir = appsDir;
        this.poolDir = poolDir;
    }

    /**
     * Removes software that is not used anymore.
     */
    public void run() {
        try {
            doCleanApps();
            doCleanLaunchers();
            doCleanup();
        } catch (Exception ex) {
            doResolveErrors(ex);
        }

        // Delete unreferenced elements
        doPrune();
    }

    /** Removes all launchers that are not required any more */
    private void doCleanLaunchers() {
        // Collect all required launchers
        ClientSoftwareManifest mf = new ClientSoftwareManifest(hive);
        Set<Key> required = mf.getRequiredLauncherKeys();

        // The currently running launcher is always required
        Set<Key> installed = getAvailableLaunchers();
        Iterator<Key> iter = installed.iterator();
        while (iter.hasNext()) {
            Key key = iter.next();
            if (key.getTag().equalsIgnoreCase(VersionHelper.getVersion().toString())) {
                iter.remove();
            }
        }

        // Remove all the software that is still required
        installed.removeAll(required);
        if (installed.isEmpty()) {
            log.info("All installed launchers are still in-use.");
            return;
        }

        // Cleanup hive and launcher
        log.info("Removing stale launchers that are not used any more...");
        for (Manifest.Key key : installed) {
            log.info("Deleting {}", key);

            Version version = VersionHelper.parse(key.getTag());
            Path launcherPath = ClientPathHelper.getHome(rootDir, version);

            // File-Locks could prevent that we can delete the folder
            // thus we first try to rename and then delete
            if (PathHelper.exists(launcherPath)) {
                Path tmpPath = launcherPath.getParent().resolve(launcherPath.getFileName() + "_delete");
                try {
                    PathHelper.moveAndDelete(launcherPath, tmpPath);
                } catch (Exception e) {
                    log.warn("Unable to delete unused pooled application.", e);
                    return;
                }
            }

            // Delete manifest as last operation
            hive.execute(new ManifestDeleteOperation().setToDelete(key));
        }

    }

    /** Removes old application versions not required anymore */
    private void doCleanApps() {
        // Collect all required software
        ClientSoftwareManifest mf = new ClientSoftwareManifest(hive);
        Set<Key> requiredApps = mf.getRequiredKeys();

        // Collect all available software in the hive
        Set<Key> availableApps = getAvailableApps();
        if (availableApps.isEmpty()) {
            log.info("No applications are installed.");
            return;
        }

        // Remove all the software that is still required
        availableApps.removeAll(requiredApps);
        if (availableApps.isEmpty()) {
            log.info("All pooled applications are still in-use.");
            return;
        }

        // Cleanup hive and pool
        log.info("Removing stale pooled applications that are not used any more...");
        for (Manifest.Key key : availableApps) {
            log.info("Deleting {}", key);

            // File-Locks could prevent that we can delete the folder
            // thus we first try to rename and then delete
            Path pooledPath = poolDir.resolve(key.directoryFriendlyName());
            if (PathHelper.exists(pooledPath)) {
                Path tmpPath = pooledPath.getParent().resolve(pooledPath.getFileName() + "_delete");
                try {
                    PathHelper.moveAndDelete(pooledPath, tmpPath);
                } catch (Exception e) {
                    log.warn("Unable to delete unused pooled application.", e);
                    return;
                }
            }

            // Delete manifest as last operation
            hive.execute(new ManifestDeleteOperation().setToDelete(key));
        }
    }

    /** Cleans the hive as well as the pool and apps directory */
    private void doCleanup() {
        // Remove pool and apps directory if they are empty
        if (PathHelper.exists(poolDir) && PathHelper.isDirEmpty(poolDir)) {
            PathHelper.deleteRecursiveRetry(poolDir);
            log.info("Removed empty pool folder {}", poolDir);
        }
        if (PathHelper.exists(appsDir) && PathHelper.isDirEmpty(appsDir)) {
            PathHelper.deleteRecursiveRetry(appsDir);
            log.info("Removed apps folder {}", appsDir);
        }
    }

    /** Removes broken software manifest entries */
    private void doResolveErrors(Exception ex) {
        log.error("Failed to cleanup unused software.", ex);

        ClientSoftwareManifest mf = new ClientSoftwareManifest(hive);
        Collection<Key> broken = mf.listBroken();
        if (broken.isEmpty()) {
            log.info("No damanged software manifests found.");
        }
        for (Key key : broken) {
            log.info("Removing broken software manifest '{}'", key);
            mf.remove(key);
        }

        // Remove damaged elements
        Set<ElementView> result = hive.execute(new FsckOperation().setRepair(true));
        if (result.isEmpty()) {
            log.info("No damaged elements found.");
        } else {
            log.warn("Found {} damanged elements.", result.size());
            for (ElementView ele : result) {
                String name = ele.getElementId().toString();
                String value = (ele instanceof DamagedObjectView dov ? (dov.getType() + " ") : "") + ele.getPathString();
                log.warn("{} - {}", name, value);
            }
        }
    }

    /** Executes the prune operation to remove unused objects */
    private void doPrune() {
        SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());
        long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();
        if (sum > 0) {
            log.info("Removed {} objects (Size={}).", result.size(), FormatHelper.formatFileSize(sum));
        }
    }

    /**
     * Returns a list of all applications available in the hive
     */
    private Set<Key> getAvailableApps() {
        Set<Key> allKeys = hive.execute(new ManifestListOperation());
        return allKeys.stream().filter(ClientCleanup::isApp).collect(Collectors.toSet());
    }

    /**
     * Returns a list of all launchers available in the hive
     */
    private Set<Key> getAvailableLaunchers() {
        Set<Key> allKeys = hive.execute(new ManifestListOperation());
        return allKeys.stream().filter(ClientCleanup::isLauncher).collect(Collectors.toCollection(HashSet::new));
    }

    /** Returns whether or not the given manifest refers to a launcher */
    private static boolean isLauncher(Key key) {
        String launcherKey = UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER;
        return key.getName().startsWith(launcherKey);
    }

    /** Returns whether or not the given manifest refers to an application */
    private static boolean isApp(Key key) {
        return !key.getName().startsWith("meta/");
    }

}
