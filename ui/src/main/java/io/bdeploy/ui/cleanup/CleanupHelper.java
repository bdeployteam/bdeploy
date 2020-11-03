package io.bdeploy.ui.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.NodeCleanupResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;

/**
 * Shared logic for cleanups on the master.
 * The first stage calculates everything to cleanup, the second stage executes the calculated actions.
 */
public class CleanupHelper {

    private static final Logger log = LoggerFactory.getLogger(CleanupHelper.class);

    private final SecurityContext securityContext;
    private final Minion minion;
    private final BHiveRegistry registry;
    private final MasterProvider provider;
    private final VersionSorterService vss;

    private static Comparator<String> intTagComparator = (a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b));

    /**
     * Constructor.
     *
     * @param context the caller's security context
     * @param minion the minion - required to be the running master.
     * @param registry the BHive registry.
     * @param provider the {@link MasterProvider} which knows how to obtain a communication channel with an instance's controlling
     *            master.
     * @param vss
     */
    public CleanupHelper(SecurityContext context, Minion minion, BHiveRegistry registry, MasterProvider provider,
            VersionSorterService vss) {
        this.securityContext = context;
        this.minion = minion;
        this.registry = registry;
        this.provider = provider;
        this.vss = vss;
    }

    /**
     * Performs the calculation of things that require cleaning upon all given instance groups and minions.
     *
     * @return the {@link List} of {@link CleanupGroup}s per instance group or minion.
     */
    public List<CleanupGroup> calculate() {
        List<CleanupGroup> groups = new ArrayList<>();

        // master cleanup (clean instance groups, skip otherwise (software repositories))
        for (String group : registry.getAll().keySet()) {
            CleanupInstanceGroupContext context = new CleanupInstanceGroupContext(group, registry.get(group), vss);
            if (context.getInstanceGroupConfiguration() != null) {
                groups.add(calculateInstanceGroup(context));
            }
        }

        // no nodes to cleanup on central. actual node cleanup for managed masters done on each master.
        if (minion.getMode() != MinionMode.CENTRAL) {
            // minions cleanup
            SortedSet<Key> instanceNodeManifestsToKeep = collectKnownInstanceNodeManifests();
            for (Map.Entry<String, MinionDto> node : minion.getMinions().entrySet()) {
                log.info("Cleaning on {}, using {} anchors.", node.getKey(), instanceNodeManifestsToKeep.size());

                RemoteService remote = node.getValue().remote;
                NodeCleanupResource scr = ResourceProvider.getVersionedResource(remote, NodeCleanupResource.class, null);
                try {
                    List<CleanupAction> actions = scr.cleanup(instanceNodeManifestsToKeep);
                    groups.add(new CleanupGroup("Perform cleanup on " + node.getKey(), node.getKey(), null, actions));
                } catch (Exception e) {
                    log.warn("Cannot perform cleanup on minion {}", node.getKey());
                    log.debug("Error details", e);
                    groups.add(new CleanupGroup("Not possible to clean offline minion " + node.getKey(), node.getKey(), null,
                            Collections.emptyList()));
                }
            }
        }
        return groups;
    }

    /**
     * Executes all actions calculated by {@link #calculate()}.
     *
     * @param groups the {@link List} of {@link CleanupGroup}s as calculated by {@link #calculate()}.
     */
    public void execute(List<CleanupGroup> groups) {
        for (CleanupGroup group : groups) {

            if (group.instanceGroup != null) {
                BHive hive = registry.get(group.instanceGroup);
                if (hive != null) {
                    for (CleanupAction action : group.actions) {
                        action.execute(securityContext, provider, hive);
                    }
                    if (!group.actions.isEmpty()) {
                        hive.execute(new PruneOperation());
                    }
                } else {
                    log.warn("Don't know how to run cleanup group {}, instance group not found", group.name);
                }
            } else if (group.minion != null) {
                RemoteService svc = minion.getMinions().getRemote(group.minion);
                if (svc != null) {
                    log.info("Performing cleanup group {} on {}", group.name, group.minion);
                    NodeCleanupResource scr = ResourceProvider.getVersionedResource(svc, NodeCleanupResource.class, null);
                    try {
                        scr.perform(group.actions);
                    } catch (Exception e) {
                        log.warn("Cannot perform cleanup on minion {}", group.minion);
                        log.debug("Error details", e);
                    }
                } else {
                    log.warn("Minion {} associated with cleanup group {} not found", group.minion, group.name);
                }
            } else {
                log.warn("Don't know how to run cleanup group {}, no instance group or minion associated", group.name);
            }
        }
    }

    /**
     * Collects {@link InstanceNodeManifest}s to <b>keep</b>.
     * Given all {@link BHive}s registered in the given {@link BHiveRegistry}, all
     * {@link InstanceNodeManifest}s that exist (also historic versions) are collected.
     *
     * @return the {@link SortedSet} of {@link Key}s which are required to be kept alive on each node.
     */
    private SortedSet<Key> collectKnownInstanceNodeManifests() {
        SortedSet<Key> result = new TreeSet<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration ig = new InstanceGroupManifest(hive).read();
            if (ig != null) {
                log.info("Gathering information for instance group {} ({})", ig.name, ig.title);

                // instance manifests
                SortedSet<Key> imfs = InstanceManifest.scan(hive, false);

                // instance node manifests referenced by imfs
                SortedSet<Key> inmfs = imfs.stream().map(key -> InstanceManifest.of(hive, key))
                        .flatMap(im -> im.getInstanceNodeManifests().values().stream())
                        .collect(Collectors.toCollection(TreeSet::new));
                result.addAll(inmfs);

                log.info("Collected {} instance node manifests", inmfs.size());
            }
            // else: not an InstanceGroup (default hive / software repository)
        }
        return result;
    }

    /**
     * Calculate cleanup actions for a single instance group depending on auto-delete and auto-uninstall
     * configuration.
     *
     * @param context the instance group context with collected data
     * @return a {@link CleanupGroup} for the given instance group
     */
    private CleanupGroup calculateInstanceGroup(CleanupInstanceGroupContext context) {
        List<CleanupAction> instanceGroupActions = new ArrayList<>();

        // for central, don't auto-uninstall. only auto-clean products which are not known to be used.
        if (minion.getMode() != MinionMode.CENTRAL) {
            // auto uninstall of old instance version
            for (Key key : context.getLatestInstanceManifests()) {
                InstanceManifest im = InstanceManifest.of(context.getHive(), key);
                if (im.getConfiguration().autoUninstall) {
                    instanceGroupActions.addAll(calculateInstance(context, im));
                }
            }
        }

        // cleanup of unused products
        if (context.getInstanceGroupConfiguration().autoDelete) {
            instanceGroupActions.addAll(calculateProducts(context));
        }

        // cleanup of meta manifests
        instanceGroupActions.addAll(calculateMetaManifests(context));

        return new CleanupGroup("Perform Cleanup on Instance Group " + context.getInstanceGroupConfiguration().name, null,
                context.getInstanceGroupConfiguration().name, instanceGroupActions);
    }

    /**
     * Find instance versions for automatic uninstallation.
     *
     * @param context the instance group context with collected data
     * @param instanceManifest the instance to check
     * @return a list of {@link CleanupAction}s for the given instance
     */
    private List<CleanupAction> calculateInstance(CleanupInstanceGroupContext context, InstanceManifest instanceManifest) {
        // find active tags from app status (what's up running)
        MasterRootResource root = ResourceProvider.getVersionedResource(
                provider.getControllingMaster(context.getHive(), instanceManifest.getManifest()), MasterRootResource.class,
                securityContext);
        MasterNamedResource namedMaster = root.getNamedMaster(context.getGroup());
        Map<String, ProcessStatusDto> appStatus = namedMaster.getStatus(instanceManifest.getConfiguration().uuid).getAppStatus();
        Set<String> activeTags = appStatus.values().stream().map(p -> p.instanceTag).collect(Collectors.toSet());

        // get instance state (what's configured and installed)
        InstanceStateRecord state = instanceManifest.getState(context.getHive()).read();

        // find installed instance versions older than "active", not "lastActive", not currently running
        SortedSet<Key> result = context.getAllInstanceManifests().stream()
                .filter(im -> im.getName().equals(instanceManifest.getManifest().getName())) //
                .filter(im -> (state.activeTag != null && intTagComparator.compare(im.getTag(), state.activeTag) < 0)
                        && !im.getTag().equals(state.lastActiveTag)) //
                .filter(im -> state.installedTags.contains(im.getTag())) //
                .filter(im -> !activeTags.contains(im.getTag())) //
                .collect(Collectors.toCollection(TreeSet::new));

        // add to context for later cleanup steps...
        context.addInstanceVersions(instanceManifest.getManifest().getName(), result);

        // calculate and return actions for calculated instance
        List<CleanupAction> actions = new ArrayList<>();
        for (Key key : result) {
            InstanceConfiguration imConfig = InstanceManifest.of(context.getHive(), key).getConfiguration();
            actions.add(new CleanupAction(CleanupType.UNINSTALL_INSTANCE_VERSION, key.toString(),
                    "Uninstall instance version \"" + imConfig.name + "\", version \"" + key.getTag() + "\""));
        }
        return actions;
    }

    /**
     * Find unused products for deletion.
     *
     * @param context the instance group context with collected data
     * @return a list of {@link CleanupActions} for products and their applications
     */
    private List<CleanupAction> calculateProducts(CleanupInstanceGroupContext context) {
        List<CleanupAction> actions = new ArrayList<>();
        Map<String, List<Key>> productsInUseMap = collectProductsInUse(context);

        for (String pName : context.getAllProductNames()) {
            List<Key> pAll = context.getAllProductVersions(pName);
            List<Key> pInst = productsInUseMap.get(pName);
            Key oldestToKeep = pInst != null && pInst.size() > 0 ? pInst.get(0) : pAll.get(pAll.size() - 1); // oldest Installed or newest

            for (Key pKey : pAll) {
                if (pKey.equals(oldestToKeep)) {
                    break;
                }

                // prepare actions for removing the product all together.
                ProductManifest pm = ProductManifest.of(context.getHive(), pKey);
                context.addManifest4deletion(pKey);
                actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, pKey.toString(),
                        "Delete product \"" + pm.getProductDescriptor().name + "\", version \"" + pm.getKey().getTag() + "\""));

                // delete applications in product: this assumes that no single application version is used in multiple products.
                for (Key appKey : pm.getApplications()) {
                    context.addManifest4deletion(appKey);
                    ApplicationManifest am = ApplicationManifest.of(context.getHive(), appKey);
                    actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, appKey.toString(),
                            "Delete Application \"" + am.getDescriptor().name + "\", version \"" + am.getKey().getTag() + "\""));
                }
            }
        }
        return actions;
    }

    /**
     * Collect all products that are in use with respect of the instance versions that will be deleted in this run.
     *
     * @param context the instance group context with collected data for
     * @return a Map containing all products in use (productKeyName -> List of productKeys sorted by tag ascending)
     */
    private Map<String, List<Key>> collectProductsInUse(CleanupInstanceGroupContext context) {
        // find the oldest instance version per instance that is "under protection", i.e. the product it uses must not be uninstalled

        // create a map with all installed instance versions (instanceKeyName -> set of tags)
        Map<String, Set<String>> installedTagsMap = context.getLatestInstanceManifests().stream()
                .collect(Collectors.toMap(Key::getName,
                        imKey -> InstanceManifest.of(context.getHive(), imKey).getState(context.getHive()).read().installedTags));
        // remove all to-be-uninstalled versions
        context.getLatestInstanceManifests().stream()
                .forEach(imKey -> Optional.ofNullable(context.getInstanceVersions4Uninstall(imKey.getName()))
                        .ifPresent(keys -> keys.stream().forEach(k -> installedTagsMap.get(imKey.getName()).remove(k.getTag()))));
        // create a map with the oldest installed instance version (instanceKeyName -> tag)
        Map<String, String> oldestTagMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : installedTagsMap.entrySet()) {
            Optional<String> min = entry.getValue().stream().min(intTagComparator);
            oldestTagMap.put(entry.getKey(), min.isPresent() ? min.get() : "0");
        }
        // create a map with the corresponding products (productKeyName -> set of productKeys)
        return context.getAllInstanceManifests().stream()
                .filter(imKey -> intTagComparator.compare(imKey.getTag(), oldestTagMap.get(imKey.getName())) >= 0)
                .map(imKey -> InstanceManifest.of(context.getHive(), imKey).getConfiguration().product)
                .collect(Collectors.toSet()).stream().collect(Collectors.groupingBy(Key::getName,
                        Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), l -> {
                            Collections.sort(l, (a, b) -> context.getComparator(a.getName()).compare(b, a));
                            return l;
                        })));

    }

    /**
     * Calculate actions for all stale {@link MetaManifest}s i.e. for all {@link Manifests} to be deleted.
     *
     * @param context the instance group context with collected data for
     * @return List of {@link CleanupAction}s
     */
    private List<CleanupAction> calculateMetaManifests(CleanupInstanceGroupContext context) {
        List<CleanupAction> actions = new ArrayList<>();
        SortedSet<Key> allImKeys = context.getHive().execute(new ManifestListOperation());
        for (Key key : allImKeys) {
            if (MetaManifest.isMetaManifest(key)
                    && !MetaManifest.isParentAlive(key, context.getHive(), context.getAllManifests4deletion())) {
                actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, key.toString(), "Delete manifest " + key));
            }
        }
        return actions;
    }

}
