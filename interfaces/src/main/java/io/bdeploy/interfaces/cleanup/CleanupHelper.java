package io.bdeploy.interfaces.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;

/**
 * Shared logic for cleanups on the master. Both immediate and two-stage cleanup is supported.
 */
public class CleanupHelper {

    private static final Logger log = LoggerFactory.getLogger(CleanupHelper.class);

    /**
     * Performs the calculation (and optionally the actual cleanup immediately) of things that require cleaning upon all given
     * minions.
     *
     * @param minions the minions to clean.
     * @param allUniqueKeysToKeep all the {@link InstanceGroupManifest} {@link Key}s to protect from deletion, see
     *            {@link #findAllUniqueKeys(BHiveRegistry)}.
     * @param immediate whether to immediately perform cleanup. In case this is <code>true</code>, the returned {@link List} is
     *            always empty.
     * @return the {@link List} of {@link CleanupGroup}s per minion. Always empty if the immediate parameter is <code>true</code>.
     */
    public static List<CleanupGroup> cleanAllMinions(SortedMap<String, RemoteService> minions, SortedSet<Key> allUniqueKeysToKeep,
            boolean immediate) {
        List<CleanupGroup> groups = new ArrayList<>();
        for (Map.Entry<String, RemoteService> slave : minions.entrySet()) {
            log.info("Cleaning on {}, using {} anchors.", slave.getKey(), allUniqueKeysToKeep.size());

            SlaveCleanupResource scr = ResourceProvider.getResource(slave.getValue(), SlaveCleanupResource.class);
            try {
                List<CleanupAction> actions = scr.cleanup(allUniqueKeysToKeep, immediate);
                if (!immediate) {
                    groups.add(new CleanupGroup("Perform cleanup on " + slave.getKey(), slave.getKey(), actions));
                }
            } catch (Exception e) {
                log.warn("Cannot perform cleanup on minion {}", slave.getKey());
                if (log.isDebugEnabled()) {
                    log.debug("Error details", e);
                }

                if (!immediate) {
                    groups.add(new CleanupGroup("Not possible to clean offline minion " + slave.getKey(), null,
                            Collections.emptyList()));
                }
            }
        }

        return groups;
    }

    /**
     * Performs all actions calculated by {@link #cleanAllMinions(SortedMap, SortedSet, boolean)} with immediate set to
     * <code>false</code>.
     *
     * @param groups the {@link List} of {@link CleanupGroup} calculated by
     *            {@link #cleanAllMinions(SortedMap, SortedSet, boolean)}.
     * @param minions the minions to clean.
     */
    public static void cleanAllMinions(List<CleanupGroup> groups, SortedMap<String, RemoteService> minions) {
        for (CleanupGroup group : groups) {
            if (group.minion == null) {
                log.warn("Don't know how to perform cleanup group {}, no minion associated", group.name);
                continue;
            }

            RemoteService svc = minions.get(group.minion);
            if (svc == null) {
                log.warn("Minion {} associated with cleanup group {} not found", group.minion, group.name);
                continue;
            }

            log.info("Performing cleanup group {} on {}", group.name, group.minion);

            SlaveCleanupResource scr = ResourceProvider.getResource(svc, SlaveCleanupResource.class);
            try {
                scr.perform(group.actions);
            } catch (Exception e) {
                log.warn("Cannot perform cleanup on minion {}", group.minion);
                if (log.isDebugEnabled()) {
                    log.debug("Error details", e);
                }
            }
        }
    }

    /**
     * Identifies things to <b>keep</b>. Given all {@link BHive}s registered in the given {@link BHiveRegistry}, all
     * {@link InstanceNodeManifest}s that exist (also historic versions) are collected.
     *
     * @param registry the {@link BHiveRegistry} containing all relevant {@link BHive}s for the current setup.
     * @return the {@link SortedSet} of {@link Key}s which are required to be kept alive on each slave.
     */
    public static SortedSet<Key> findAllUniqueKeys(BHiveRegistry registry) {
        SortedSet<Key> allUniqueKeysToKeep = new TreeSet<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            BHive toCheck = entry.getValue();
            InstanceGroupConfiguration ig = new InstanceGroupManifest(toCheck).read();
            if (ig == null) {
                // not an instance group, skip.
                // this is either the default hive (slave hive), or a software repository.
                continue;
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
        return allUniqueKeysToKeep;
    }

}
