package io.bdeploy.ui.cleanup;

import java.util.ArrayList;
import java.util.Collection;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.NodeCleanupResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Shared logic for cleanups on the master.
 * The first stage calculates everything to cleanup, the second stage executes the calculated actions.
 */
public class CleanupHelper {

    private static final Logger log = LoggerFactory.getLogger(CleanupHelper.class);
    private static Comparator<String> intTagComparator = (a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b));

    private final SecurityContext securityContext;
    private final Minion minion;
    private final BHiveRegistry registry;
    private final MasterProvider provider;
    private final VersionSorterService vss;

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
        log.info("Cleanup start");
        List<CleanupGroup> groups = new ArrayList<>();

        SortedSet<Key> instanceNodeManifestsToKeep = new TreeSet<>();

        // master cleanup (clean instance groups, skip otherwise (software repositories))
        for (String group : registry.getAll().keySet()) {
            log.info("Checking group {}...", group);
            try {
                CleanupInstanceGroupContext context = new CleanupInstanceGroupContext(group, registry.get(group), vss);
                if (context.getInstanceGroupConfiguration() != null) {
                    CleanupGroup cleanupGroup = calculateInstanceGroup(context);
                    groups.add(cleanupGroup);
                    instanceNodeManifestsToKeep.addAll(context.getAllInstanceNodeManifestsToKeep());
                }
            } catch (Exception e) {
                log.warn("Unexpected Exception while calculating cleanup for {}", group, e);
            }
        }

        // no nodes to cleanup on central. actual node cleanup for managed masters done on each master.
        if (minion.getMode() != MinionMode.CENTRAL) {
            // minions cleanup
            NodeManager nodes = minion.getNodeManager();
            Collection<String> nodeNames = nodes.getAllNodeNames();

            for (String nodeName : nodeNames) {
                log.info("Calculate node {}, using {} anchors.", nodeName, instanceNodeManifestsToKeep.size());

                try {
                    List<CleanupAction> actions = nodes
                            .getNodeResourceIfOnlineOrThrow(nodeName, NodeCleanupResource.class, securityContext)
                            .cleanup(instanceNodeManifestsToKeep);
                    groups.add(new CleanupGroup("Node " + nodeName, nodeName, null, actions));
                } catch (Exception e) {
                    log.warn("Cannot perform cleanup on minion {}", nodeName);
                    if (log.isDebugEnabled()) {
                        log.debug("Error details", e);
                    }
                    groups.add(new CleanupGroup("Not possible to clean offline minion " + nodeName, nodeName, null,
                            Collections.emptyList()));
                }
            }
        }
        log.info("Cleanup calculation done.");
        return groups;
    }

    /**
     * Executes all actions calculated by {@link #calculate()}.
     *
     * @param groups the {@link List} of {@link CleanupGroup}s as calculated by {@link #calculate()}.
     */
    public void execute(Iterable<CleanupGroup> groups) {
        for (CleanupGroup group : groups) {
            if (group.instanceGroup != null) {
                executeForGroup(group);
            } else if (group.minion != null) {
                executeForMinion(group);
            } else {
                log.warn("Don't know how to run cleanup group {}, no instance group or minion associated", group.name);
            }
        }
    }

    private void executeForGroup(CleanupGroup group) {
        BHive hive = registry.get(group.instanceGroup);
        if (hive == null) {
            log.warn("Don't know how to run cleanup group {}, instance group not found", group.name);
            return;
        }
        for (CleanupAction action : group.actions) {
            action.execute(securityContext, provider, hive);
        }

        // always prune to clean up things that are left over even if WE did nothing.
        // this targets mainly "rolling" manifests which are updated (created & deleted) in a rolling
        // fashion like meta-manifests keeping track of things. Since those things are not created
        // on a central, *and* central is the one with *a lot* of hives, we do this only on non-central.
        if (minion.getMode() != MinionMode.CENTRAL || !group.actions.isEmpty()) {
            hive.execute(new PruneOperation());
        }
    }

    private void executeForMinion(CleanupGroup group) {
        NodeManager nodes = minion.getNodeManager();
        try {
            nodes.getNodeResourceIfOnlineOrThrow(group.minion, NodeCleanupResource.class, securityContext).perform(group.actions);
        } catch (Exception e) {
            log.warn("Cannot perform cleanup on minion {}: {}", group.minion, e.toString());
            if (log.isDebugEnabled()) {
                log.debug("Error details", e);
            }
        }
    }

    /**
     * Calculate cleanup actions for a single instance group depending on auto-delete and auto-uninstall
     * configuration.
     *
     * @param context the instance group context with collected data
     * @return a {@link CleanupGroup} for the given instance group
     */
    private CleanupGroup calculateInstanceGroup(CleanupInstanceGroupContext context) {
        log.info("Calculate instance group {}", context.getInstanceGroupConfiguration().name);
        List<CleanupAction> instanceGroupActions = new ArrayList<>();

        // for central, don't auto-uninstall. only auto-clean products which are not known to be used.
        if (minion.getMode() != MinionMode.CENTRAL) {
            // auto uninstall of old instance version
            for (Key key : context.getLatestInstanceManifests()) {
                instanceGroupActions.addAll(calculateInstance(context, key));
            }
        }

        // cleanup of unused products
        if (context.getInstanceGroupConfiguration().autoDelete) {
            instanceGroupActions.addAll(calculateProducts(context));
        }

        // cleanup of meta manifests
        instanceGroupActions.addAll(calculateMetaManifests(context));

        return new CleanupGroup("Group " + context.getInstanceGroupConfiguration().name, null,
                context.getInstanceGroupConfiguration().name, instanceGroupActions);
    }

    /**
     * Find instance versions for automatic uninstallation.
     *
     * @param context the instance group context with collected data
     * @param instanceManifest the instance to check
     * @return a list of {@link CleanupAction}s for the given instance
     */
    private List<CleanupAction> calculateInstance(CleanupInstanceGroupContext context, Key imKey) {
        log.info("Calculate instance {} in group {}", imKey, context.getInstanceGroupConfiguration().name);
        List<CleanupAction> actions = new ArrayList<>();

        InstanceManifest instanceManifest = InstanceManifest.of(context.getHive(), imKey);

        if (instanceManifest.getConfiguration().autoUninstall) {
            log.debug("Auto-delete is requested, checking for old versions of {} in group {}", imKey,
                    context.getInstanceGroupConfiguration().name);

            // find active tags from app status (what's up running)
            MasterRootResource root = ResourceProvider.getVersionedResource(
                    provider.getControllingMaster(context.getHive(), imKey), MasterRootResource.class, securityContext);
            MasterNamedResource namedMaster = root.getNamedMaster(context.getGroup());
            Map<String, ProcessStatusDto> appStatus = namedMaster.getStatus(instanceManifest.getConfiguration().id)
                    .getAppStatus();
            Set<String> activeTags = appStatus.values().stream().map(p -> p.instanceTag).collect(Collectors.toSet());

            log.info("Applications are running from tags: {}", activeTags);

            // get instance state (what's configured and installed)
            InstanceStateRecord state = instanceManifest.getState(context.getHive()).read();

            // find installed instance versions older than "active", not "lastActive", not currently running
            SortedSet<Key> result = context.getAllInstanceManifests().stream().filter(im -> im.getName().equals(imKey.getName())) //
                    .filter(im -> (state.activeTag != null && intTagComparator.compare(im.getTag(), state.activeTag) < 0)
                            && !im.getTag().equals(state.lastActiveTag)) //
                    .filter(im -> state.installedTags.contains(im.getTag())) //
                    .filter(im -> !activeTags.contains(im.getTag())) //
                    .collect(Collectors.toCollection(TreeSet::new));

            log.info("Found old versions to uninstall: {}", result);

            // add to context for later cleanup steps...
            context.addInstanceVersions(imKey.getName(), result);

            // calculate the instance node manifests to keep during minion cleanup
            // (it's calculated here because required information is available at this point)
            SortedSet<Key> inmfs = context.getAllInstanceManifests().stream() //
                    .filter(im -> im.getName().equals(imKey.getName())) //
                    .map(key -> InstanceManifest.of(context.getHive(), key)) //
                    .flatMap(im -> im.getInstanceNodeManifestKeys().values().stream()) //
                    .filter(inm -> (state.activeTag != null && intTagComparator.compare(inm.getTag(), state.activeTag) >= 0)
                            || inm.getTag().equals(state.lastActiveTag) //
                            || activeTags.contains(inm.getTag())) //
                    .collect(Collectors.toCollection(TreeSet::new));
            context.addInstanceNodeManifestsToKeep(inmfs);

            // calculate actions for calculated instance
            for (Key key : result) {
                InstanceConfiguration imConfig = InstanceManifest.of(context.getHive(), key).getConfiguration();
                actions.add(new CleanupAction(CleanupType.UNINSTALL_INSTANCE_VERSION, key.toString(),
                        "Uninstall instance version \"" + imConfig.name + "\", version \"" + key.getTag() + "\""));
            }
        } else {
            log.debug("No auto-delete for {} configured, skipping", imKey);

            // keep all InstanceNodeManifests on minion
            SortedSet<Key> inmfs = context.getAllInstanceManifests().stream() //
                    .filter(im -> im.getName().equals(imKey.getName())) //
                    .map(key -> InstanceManifest.of(context.getHive(), key))
                    .flatMap(im -> im.getInstanceNodeManifestKeys().values().stream())
                    .collect(Collectors.toCollection(TreeSet::new));
            context.addInstanceNodeManifestsToKeep(inmfs);
        }
        return actions;
    }

    /**
     * Find unused products for deletion.
     *
     * @param context the instance group context with collected data
     * @return a list of {@link CleanupActions} for products and their applications
     */
    private static List<CleanupAction> calculateProducts(CleanupInstanceGroupContext context) {
        log.info("Calculate products to clean in group {}", context.getInstanceGroupConfiguration().name);

        List<CleanupAction> actions = new ArrayList<>();
        Map<String, List<Key>> productsInUseMap = collectProductsInUse(context);

        for (String pName : context.getAllProductNames()) {
            List<Key> pAll = context.getAllProductVersions(pName);
            List<Key> pInst = productsInUseMap.get(pName);
            Key oldestToKeep = pInst != null && !pInst.isEmpty() ? pInst.get(0) : pAll.get(pAll.size() - 1); // oldest Installed or newest

            log.info("Oldest product to keep for product {}, older will be removed if possible: {}", pName, oldestToKeep);

            Comparator<Key> comparator = context.getComparator(pAll.get(0));
            for (Key pKey : pAll) {
                // oldestToKeep can be a version that doesn't exist any more and therefore is not in pAll!
                // note: comparison here works "name asc"/"tag desc"!
                if (oldestToKeep != null && comparator.compare(oldestToKeep, pKey) >= 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Version is newer, will keep: {}", pKey);
                    }
                    break;
                }

                // Check if the version isn't installed. Should never be true at this point but in cases where the product sort order isn't correct,
                // this check will at least protect installed versions
                if (pInst == null || !pInst.contains(pKey)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Version is older and not used, will remove: {}", pKey);
                    }

                    // prepare actions for removing the product all together.
                    ProductManifest pm = ProductManifest.of(context.getHive(), pKey);
                    context.addManifest4deletion(pKey);
                    actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, pKey.toString(), "Delete product \""
                            + pm.getProductDescriptor().name + "\", version \"" + pm.getKey().getTag() + "\""));

                    // delete applications in product: this assumes that no single application version is used in multiple products.
                    for (Key appKey : pm.getApplications()) {
                        context.addManifest4deletion(appKey);
                        actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, appKey.toString(), "Delete Application \""
                                + appKey.getName() + "\", version \"" + appKey.getTag() + "\""));
                    }
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
    private static Map<String, List<Key>> collectProductsInUse(CleanupInstanceGroupContext context) {
        // find the oldest instance version per instance that is "under protection", i.e. the product it uses must not be uninstalled

        // create a map with all installed instance versions (instanceKeyName -> set of tags)
        Map<String, Set<String>> installedTagsMap = context.getLatestInstanceManifests().stream()
                .collect(Collectors.toMap(Key::getName,
                        imKey -> InstanceManifest.of(context.getHive(), imKey).getState(context.getHive()).read().installedTags));

        // remove all to-be-uninstalled versions
        context.getLatestInstanceManifests().stream()
                .forEach(imKey -> Optional.ofNullable(context.getInstanceVersions4Uninstall(imKey.getName()))
                        .ifPresent(keys -> keys.stream().forEach(k -> installedTagsMap.get(imKey.getName()).remove(k.getTag()))));

        if (log.isDebugEnabled()) {
            for (Map.Entry<String, Set<String>> entry : installedTagsMap.entrySet()) {
                log.debug("Considering remaining installed for instance {}: {}", entry.getKey(), entry.getValue());
            }
        }

        // create a map with the oldest installed instance version (instanceKeyName -> tag)
        Map<String, String> oldestTagMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : installedTagsMap.entrySet()) {
            Optional<String> min = entry.getValue().stream().min(intTagComparator);
            oldestTagMap.put(entry.getKey(), min.isPresent() ? min.get() : "0");

            log.info("Considering as oldest protected instance version for {}: {}", entry.getKey(),
                    oldestTagMap.get(entry.getKey()));
        }

        // create a map with the corresponding products (productKeyName -> set of productKeys)
        Map<String, List<Key>> result = context.getAllInstanceManifests().stream()
                .filter(imKey -> intTagComparator.compare(imKey.getTag(), oldestTagMap.get(imKey.getName())) >= 0)
                .map(imKey -> InstanceManifest.of(context.getHive(), imKey).getConfiguration().product)
                .collect(Collectors.toSet()).stream().collect(Collectors.groupingBy(Key::getName,
                        Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), l -> {
                            Collections.sort(l, (a, b) -> context.getComparator(a).compare(b, a));
                            return l;
                        })));

        if (log.isDebugEnabled()) {
            for (Map.Entry<String, List<Key>> entry : result.entrySet()) {
                log.debug("Product versions kept alive by versions of {}, newer than oldest protected version: {}",
                        entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Calculate actions for all stale {@link MetaManifest}s i.e. for all {@link Manifests} to be deleted.
     *
     * @param context the instance group context with collected data for
     * @return List of {@link CleanupAction}s
     */
    private static List<CleanupAction> calculateMetaManifests(CleanupInstanceGroupContext context) {
        log.info("Calculate stale meta-manifests in group {}", context.getInstanceGroupConfiguration().name);

        List<CleanupAction> actions = new ArrayList<>();
        Set<Key> allMMKeys = context.getHive().execute(new ManifestListOperation().setManifestName(MetaManifest.META_PREFIX));
        Set<String> toTruncateMetas = new TreeSet<>();
        for (Key key : allMMKeys) {
            // all meta manifest that are not attached anymore are fixed candidates to remove.
            if (!MetaManifest.isParentAlive(key, context.getHive(), context.getAllManifests4deletion())) {
                actions.add(new CleanupAction(CleanupType.DELETE_MANIFEST, key.toString(), "Delete manifest " + key));
                continue;
            }

            if (toTruncateMetas.contains(key.getName())) {
                // no need to check again, especially if it has more than META_HIST_SIZE tags.
                continue;
            }

            // list all tags of the given meta-manifest, to see how many there are.
            if (context.getHive().execute(new ManifestListOperation().setManifestName(key.getName()))
                    .size() > MetaManifest.META_HIST_SIZE) {
                // truncate the history of each meta manifest, so they don't amass.
                toTruncateMetas.add(key.getName());
            }
        }

        // this *should* only happen on central, but we still perform the check on each server.
        toTruncateMetas.forEach(
                m -> actions.add(new CleanupAction(CleanupType.TRUNCATE_META_MANIFEST, m, "Truncate meta manifest " + m)));

        return actions;
    }

}
