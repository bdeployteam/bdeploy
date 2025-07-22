package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
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
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

/**
 * Cleans software from the hive which is not used any more. The {@linkplain ClientSoftwareManifest} is used in
 * order to determine which software is used and which one not. Applications listed in the hive which are not referenced by any
 * manifest are removed from the hive and from the pool. Only the latest version of the manifest is taken into account.
 */
public class ClientCleanup {

    private static final Logger log = LoggerFactory.getLogger(ClientCleanup.class);

    private final BHive hive;
    private final LauncherPathProvider lpp;

    /**
     * Creates a new cleanup instance using the given hive
     */
    public ClientCleanup(BHive hive, LauncherPathProvider lpp) {
        this.hive = hive;
        this.lpp = lpp;
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

    /** Removes old application versions not required anymore */
    private void doCleanApps() {
        // Collect all available software in the hive
        Set<Key> availableApps = getAvailableApps();
        if (availableApps.isEmpty()) {
            log.info("No applications are installed.");
            return;
        }

        // Remove all the software that is still required
        Set<Key> requiredApps = new ClientSoftwareManifest(hive).getRequiredKeys();
        availableApps.removeAll(requiredApps);
        if (availableApps.isEmpty()) {
            log.info("All pooled applications are still in-use.");
            return;
        }

        // Cleanup hive and pool
        log.info("Removing stale pooled applications that are not used any more...");
        Path graveyardDir = lpp.get(SpecialDirectory.GRAVEYARD);
        try {
            Files.createDirectories(graveyardDir);
        } catch (IOException e) {
            log.error("Failed to create graveyard directory at {}", graveyardDir, e);
            return;
        }
        Path poolDir = lpp.get(SpecialDirectory.MANIFEST_POOL);
        for (Manifest.Key key : availableApps) {
            log.info("Deleting {}", key);

            // File-Locks could prevent that we can delete the folder, thus we just move it away and delete it later
            Path pooledPath = poolDir.resolve(key.directoryFriendlyName());
            if (PathHelper.exists(pooledPath)) {
                Path tmpPath = graveyardDir.resolve(pooledPath.getFileName());
                try {
                    PathHelper.moveRetry(pooledPath, tmpPath, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log.warn("Failed to move unused pooled application to graveyard: {}", key, e);
                    continue;
                }
            }

            // Delete manifest as last operation
            hive.execute(new ManifestDeleteOperation().setToDelete(key));
        }
    }

    /**
     * Removes all launchers that are not required anymore.
     *
     * @deprecated The delegated launcher removal in 7.2.0 means that no delegate launchers are created anymore,
     *             and therefore no cleaning of them should be required. However, this code is still kept so that
     *             old delegate launchers that are left over from pre-updated launchers are being deleted.
     */
    @Deprecated(since = "7.2.0")
    private void doCleanLaunchers() {
        String launcherKey = UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER;
        String currentLauncherVersion = VersionHelper.getVersion().toString();
        Set<Key> launchersToDelete = hive.execute(new ManifestListOperation()).stream()//
                .filter(key -> key.getName().startsWith(launcherKey))//
                .filter(key -> !currentLauncherVersion.equalsIgnoreCase(key.getTag()))//
                .collect(Collectors.toCollection(HashSet::new));
        if (launchersToDelete.isEmpty()) {
            return;
        }

        // Cleanup hive and launcher
        log.info("Removing stale launchers that are not used anymore...");
        Path homeDir = lpp.get(SpecialDirectory.HOME);
        for (Manifest.Key key : launchersToDelete) {
            log.info("Deleting {}", key);

            Version version = VersionHelper.parse(key.getTag());
            Path versionedHome = ClientPathHelper.getVersionedHome(homeDir, version);

            // File-Locks could prevent that we can delete the folder
            // thus we first try to rename and then delete
            if (PathHelper.exists(versionedHome)) {
                Path tmpPath = versionedHome.getParent().resolve(versionedHome.getFileName() + "_delete");
                try {
                    PathHelper.moveAndDelete(versionedHome, tmpPath);
                } catch (Exception e) {
                    log.warn("Unable to delete unused pooled application.", e);
                    return;
                }
            }

            // Delete manifest as last operation
            hive.execute(new ManifestDeleteOperation().setToDelete(key));
        }
    }

    /** Cleans the pool, scripts, apps directory and graveyard */
    private void doCleanup() {
        deleteDirIfEmpty(lpp.get(SpecialDirectory.MANIFEST_POOL));
        deleteDirIfEmpty(lpp.get(SpecialDirectory.START_SCRIPTS));
        deleteDirIfEmpty(lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS));
        deleteDirIfEmpty(lpp.get(SpecialDirectory.APPS));
        PathHelper.deleteRecursiveRetry(lpp.get(SpecialDirectory.GRAVEYARD));
    }

    private static boolean deleteDirIfEmpty(Path path) {
        if (PathHelper.exists(path) && PathHelper.isDirEmpty(path)) {
            PathHelper.deleteRecursiveRetry(path);
            log.info("Removed empty folder: {}", path.getFileName());
            return true;
        }
        return false;
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

    /** Returns whether or not the given manifest refers to an application */
    private static boolean isApp(Key key) {
        return !key.getName().startsWith("meta/");
    }
}
