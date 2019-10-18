package io.bdeploy.interfaces.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;

/**
 * Shared logic for cleanups on the master. Both immediate and two-stage cleanup is supported.
 */
public class CleanupHelper {

    private static final Logger log = LoggerFactory.getLogger(CleanupHelper.class);

    private CleanupHelper() {
    }

    /**
     * Performs the calculation (and optionally the actual cleanup immediately) of things that require cleaning upon all given
     * minions.
     *
     * @param context the caller's security context
     * @param minions the minions to clean.
     * @param registry the BHive registry.
     * @param immediate whether to immediately perform cleanup. In case this is <code>true</code>, the returned {@link List} is
     *            always empty.
     * @return the {@link List} of {@link CleanupGroup}s per minion. Always empty if the immediate parameter is <code>true</code>.
     */
    public static List<CleanupGroup> cleanAllMinions(SecurityContext context, SortedMap<String, RemoteService> minions,
            BHiveRegistry registry, boolean immediate) {

        List<CleanupGroup> groups = new ArrayList<>();

        // master cleanup
        for (String group : registry.getAll().keySet()) {
            BHive hive = registry.get(group);

            InstanceGroupConfiguration cfg = new InstanceGroupManifest(hive).read();
            if (cfg != null) {
                List<CleanupAction> instanceGroupActions = new ArrayList<>();
                TreeSet<Key> allInstanceVersions4Uninstall = new TreeSet<>();

                // auto uninstall of old instance version
                SortedSet<Key> latestImKeys = InstanceManifest.scan(hive, true);
                for (Key key : latestImKeys) {
                    InstanceManifest im = InstanceManifest.of(hive, key);
                    if (im.getConfiguration().autoUninstall) {
                        TreeSet<Key> instanceVersions4Uninstall = findInstanceVersions4Uninstall(context, group, hive, im);
                        allInstanceVersions4Uninstall.addAll(instanceVersions4Uninstall);
                        instanceGroupActions
                                .addAll(uninstallInstanceVersions(context, hive, instanceVersions4Uninstall, immediate));
                    }
                }

                // cleanup of unused products
                if (cfg.autoDelete) {
                    instanceGroupActions.addAll(deleteUnusedProducts(context, hive, allInstanceVersions4Uninstall, immediate));
                }

                if (!immediate) {
                    groups.add(new CleanupGroup("Perform Cleanup on Instance Group " + cfg.name, null, cfg.name,
                            instanceGroupActions));
                }
            }
        }

        // minions cleanup
        SortedSet<Key> allUniqueKeysToKeep = findAllUniqueKeys(registry);
        for (Map.Entry<String, RemoteService> slave : minions.entrySet()) {
            log.info("Cleaning on {}, using {} anchors.", slave.getKey(), allUniqueKeysToKeep.size());

            SlaveCleanupResource scr = ResourceProvider.getResource(slave.getValue(), SlaveCleanupResource.class, null);
            try {
                List<CleanupAction> actions = scr.cleanup(allUniqueKeysToKeep, immediate);
                if (!immediate) {
                    groups.add(new CleanupGroup("Perform cleanup on " + slave.getKey(), slave.getKey(), null, actions));
                }
            } catch (Exception e) {
                log.warn("Cannot perform cleanup on minion {}", slave.getKey());
                if (log.isDebugEnabled()) {
                    log.debug("Error details", e);
                }

                if (!immediate) {
                    groups.add(new CleanupGroup("Not possible to clean offline minion " + slave.getKey(), slave.getKey(), null,
                            Collections.emptyList()));
                }
            }
        }

        return groups;
    }

    /**
     * Performs all actions calculated by {@link #cleanAllMinions(SecurityContext, SortedMap, BHiveRegistry, boolean)} with
     * immediate set to <code>false</code>.
     *
     * @param context the caller's security context
     * @param groups the {@link List} of {@link CleanupGroup} calculated by
     *            {@link #cleanAllMinions(SecurityContext, SortedMap, BHiveRegistry, boolean)}.
     * @param minions the minions to clean.
     * @param registry the BHive registry.
     */
    public static void cleanAllMinions(SecurityContext context, List<CleanupGroup> groups,
            SortedMap<String, RemoteService> minions, BHiveRegistry registry) {
        for (CleanupGroup group : groups) {
            if (group.instanceGroup != null) {
                BHive hive = registry.get(group.instanceGroup);
                if (hive == null) {
                    log.warn("Don't know how to perform cleanup group {}, instance group not found", group.name);
                    continue;
                }
                perform(context, hive, group.actions);
                continue;
            }
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

            SlaveCleanupResource scr = ResourceProvider.getResource(svc, SlaveCleanupResource.class, null);
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

    public static TreeSet<Key> findInstanceVersions4Uninstall(SecurityContext context, String group, BHive hive,
            InstanceManifest instanceManifest) {
        InstanceStateRecord state = instanceManifest.getState(hive).read();

        RemoteService rs = instanceManifest.getConfiguration().target;
        MasterNamedResource namedMaster = ResourceProvider.getResource(rs, MasterRootResource.class, context)
                .getNamedMaster(group);

        Map<String, ProcessStatusDto> appStatus = namedMaster.getStatus(instanceManifest.getConfiguration().uuid).getAppStatus();

        Set<String> activeTags = appStatus.values().stream().map(p -> p.instanceTag).collect(Collectors.toSet());

        return InstanceManifest.scan(hive, false).stream()
                .filter(im -> im.getName().equals(instanceManifest.getManifest().getName())) //
                .filter(im -> (state.activeTag == null || im.getTag().compareTo(state.activeTag) < 0)
                        && !im.getTag().equals(state.lastActiveTag)) //
                .filter(im -> state.installedTags.contains(im.getTag())) //
                .filter(im -> !activeTags.contains(im.getTag())) //
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static List<CleanupAction> uninstallInstanceVersions(SecurityContext context, BHive hive, Set<Key> imVersionKeys,
            boolean immediate) {
        List<CleanupAction> actions = new ArrayList<>();

        for (Key key : imVersionKeys) {
            InstanceConfiguration imConfig = InstanceManifest.of(hive, key).getConfiguration();
            actions.add(new CleanupAction(CleanupType.UNINSTALL_INSTANCE_VERSION, key.toString(),
                    "Uninstall instance version \"" + imConfig.name + "\", version \"" + key.getTag() + "\""));
        }

        if (immediate) {
            perform(context, hive, actions);
            return Collections.emptyList();
        }

        return actions;
    }

    public static List<CleanupAction> deleteUnusedProducts(SecurityContext context, BHive hive,
            Set<Key> instanceVersions4Uninstall, boolean immediate) {
        List<CleanupAction> actions = new ArrayList<>();

        // map of available products grouped by product name
        Map<String, TreeSet<Key>> allProductsMap = ProductManifest.scan(hive).stream()
                .collect(Collectors.groupingBy(Key::getName, Collectors.toCollection(TreeSet::new)));

        // map of installed products grouped by product name
        Map<String, InstanceStateRecord> statesMap = InstanceManifest.scan(hive, true).stream().collect(
                Collectors.toMap(imKey -> imKey.getName(), imKey -> InstanceManifest.of(hive, imKey).getState(hive).read()));
        Map<String, TreeSet<Key>> usedProductsMap = InstanceManifest.scan(hive, false).stream()
                .filter(imKey -> statesMap.get(imKey.getName()).installedTags.contains(imKey.getTag()))
                .filter(imKey -> !instanceVersions4Uninstall.contains(imKey))
                .map(imKey -> InstanceManifest.of(hive, imKey).getConfiguration().product).collect(Collectors.toSet()).stream()
                .collect(Collectors.groupingBy(Key::getName, Collectors.toCollection(TreeSet::new)));

        // create actions for unused products (older than the oldest product in use)
        for (TreeSet<Key> pVersionKeys : allProductsMap.values()) {
            TreeSet<Key> usedProducts = usedProductsMap.get(pVersionKeys.first().getName());
            Key oldestInUse = usedProducts != null && !usedProducts.isEmpty() ? usedProducts.first() : null;
            for (Key versionKey : pVersionKeys) {
                // stop at the oldest product in use)
                if (oldestInUse != null && versionKey.compareTo(oldestInUse) >= 0) {
                    break;
                }
                // If sorting of tags is broken, it should affect both sets (allProductsMap AND usedProducts) and this should
                // protect all used products in use, but it's still being checked here again ;-)
                if (usedProducts == null || !usedProducts.contains(versionKey)) {
                    ProductManifest pm = ProductManifest.of(hive, versionKey);
                    actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, versionKey.toString(), "Delete product \""
                            + pm.getProductDescriptor().name + "\", version \"" + pm.getKey().getTag() + "\""));

                    // delete applications in product: this assumes that no single application version is used in multiple products.
                    for (Key appKey : pm.getApplications()) {
                        ApplicationManifest am = ApplicationManifest.of(hive, appKey);
                        actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, appKey.toString(), "Delete Application \""
                                + am.getDescriptor().name + "\", version \"" + am.getKey().getTag() + "\""));
                    }
                }
            }
        }

        if (immediate) {
            perform(context, hive, actions);
            return Collections.emptyList();
        }

        return actions;
    }

    public static void perform(SecurityContext context, BHive hive, List<CleanupAction> actions) {
        for (CleanupAction action : actions) {
            switch (action.type) {
                case UNINSTALL_INSTANCE_VERSION:
                    Key imKey = Key.parse(action.what);
                    InstanceGroupConfiguration igc = new InstanceGroupManifest(hive).read();
                    InstanceManifest im = InstanceManifest.of(hive, imKey);
                    RemoteService svc = im.getConfiguration().target;

                    MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context);
                    master.getNamedMaster(igc.name).uninstall(imKey);
                    break;
                case DELETE_MANIFEST:
                    hive.execute(new ManifestDeleteOperation().setToDelete(Key.parse(action.what)));
                    break;
                default:
                    throw new IllegalStateException("CleanupType " + action.type + " not supported here");
            }
        }

        if (!actions.isEmpty()) {
            hive.execute(new PruneOperation());
        }
    }

}
