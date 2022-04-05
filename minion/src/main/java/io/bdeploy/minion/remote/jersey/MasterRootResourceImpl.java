package io.bdeploy.minion.remote.jersey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.SortOneAsLastComparator;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.MinionUpdateResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.dto.BackendInfoDto;
import io.bdeploy.ui.dto.InstanceDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class MasterRootResourceImpl implements MasterRootResource {

    private static final Logger log = LoggerFactory.getLogger(MasterRootResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private NodeManager nodes;

    @Context
    private ResourceContext rc;

    @Context
    private SecurityContext context;

    @Override
    public Map<String, MinionStatusDto> getNodes() {
        return nodes.getAllNodeStatus();
    }

    @Override
    public void addNode(String name, RemoteService minion) {
        nodes.addNode(name, MinionDto.create(false, minion));
    }

    @Override
    public void convertNode(String name, RemoteService minion) {
        try (Activity finding = reporter.start("Migrating " + name)) {
            // first, contact and verify information.
            MinionMigrationState state = checkAndPrepareNodesForMigration(name, minion);

            // make sure all instance groups on the source server also exist on the target (this) server.
            checkAndPrepareInstanceGroupsForMigration(minion, state);

            // fetch all instances from all instance groups
            checkAndCollectInstancesForMigration(minion, state);

            // transfer all data and post process instances on the go.
            transferAndProcessInstances(minion, state);

            // tell the now-node that it is a node now, and it should restart.
            ResourceProvider.getResource(minion, MinionUpdateResource.class, context).convertToNode();

            // last step, add all nodes.
            state.allNodes.forEach((k, v) -> nodes.addNode(k, v.config));
        }
    }

    private void checkAndCollectInstancesForMigration(RemoteService minion, MinionMigrationState state) {
        InstanceGroupResource remoteIgr = ResourceProvider.getResource(minion, InstanceGroupResource.class, context);

        try (Activity finding = reporter.start("Finding Instances for Migration", state.allGroups.size())) {
            for (InstanceGroupConfiguration igc : state.allGroups) {
                InstanceResource remoteIr = remoteIgr.getInstanceResource(igc.name);

                List<InstanceDto> instances = remoteIr.list();
                state.allInstances.put(igc.name, instances);
                Map<String, List<Manifest.Key>> toFetch = state.toFetch.computeIfAbsent(igc.name, k -> new TreeMap<>());

                for (InstanceDto instance : instances) {
                    List<Manifest.Key> toFetchForInstance = new ArrayList<>();
                    toFetchForInstance.add(instance.activeVersion != null ? instance.activeVersion : instance.latestVersion);
                    toFetchForInstance.add(instance.activeProduct != null ? instance.activeProduct : instance.productDto.key);
                    toFetch.put(instance.instanceConfiguration.uuid, toFetchForInstance);
                }
            }
            finding.workAndCancelIfRequested(1);
        }
    }

    private void transferAndProcessInstances(RemoteService minion, MinionMigrationState state) {
        try (Activity fetching = reporter.start("Transferring Data for Migration")) {
            for (Map.Entry<String, Map<String, List<Manifest.Key>>> grpEntry : state.toFetch.entrySet()) {
                BHive grpHive = registry.get(grpEntry.getKey()); // must exist, see previous steps.

                for (Map.Entry<String, List<Manifest.Key>> entry : grpEntry.getValue().entrySet()) {
                    // only fetch things which are not there yet. FetchOperation would do this automatically,
                    // but we want to know what to delete in case post-processing goes wrong.
                    List<Key> toFetch = entry.getValue().stream()
                            .filter(k -> Boolean.FALSE.equals(grpHive.execute(new ManifestExistsOperation().setManifest(k))))
                            .toList();

                    try (Transaction tx = grpHive.getTransactions().begin()) {
                        // actually fetch.
                        grpHive.execute(
                                new FetchOperation().setRemote(minion).setHiveName(grpEntry.getKey()).addManifest(toFetch));

                        // TODO: fetch instance runtimeDependencies overrides here once implemented.
                    }

                    try {
                        // replace oldMasterName in node configs with the new name. we do this immediately
                        // so that we have a chance to remove things we fetched in case we encounter an error.
                        postProcessInstance(grpEntry.getKey(), entry.getKey(), state.originalName, state.newName);
                    } catch (Exception e) {
                        // in case of *any* exception while post-processing the instance, we want to get rid of things we fetched.
                        log.error("Cannot perform migration on instance {}: {}", entry.getKey(), e.toString());
                        if (log.isDebugEnabled()) {
                            log.debug("Error Details:", e);
                        }

                        // remove all the manifests for this instance again.
                        toFetch.forEach(m -> grpHive.execute(new ManifestDeleteOperation().setToDelete(m)));
                    }
                }
            }
        }
    }

    private void postProcessInstance(String instanceGroup, String instance, String oldNodeName, String newNodeName) {
        BHive hive = registry.get(instanceGroup);

        String rootName = InstanceManifest.getRootName(instance);
        Optional<Long> latest = hive.execute(new ManifestMaxIdOperation().setManifestName(rootName));
        if (latest.isEmpty()) {
            // instance not found?!
            throw new WebApplicationException("Instance not found after fetching: " + instance, Status.EXPECTATION_FAILED);
        }

        InstanceManifest existing = InstanceManifest.of(hive, new Manifest.Key(rootName, String.valueOf(latest.get())));

        InstanceManifest.Builder imfb = new InstanceManifest.Builder().setInstanceConfiguration(existing.getConfiguration());
        for (Map.Entry<String, InstanceNodeConfiguration> node : existing.getInstanceNodeConfiguration(hive).entrySet()) {
            InstanceNodeManifest.Builder inmBuilder = new InstanceNodeManifest.Builder();
            InstanceNodeConfiguration nodeCfg = node.getValue();

            String minionName = node.getKey();
            if (minionName.equals(oldNodeName)) {
                minionName = newNodeName;
            }

            inmBuilder.setConfigTreeId(existing.getConfiguration().configTree);
            inmBuilder.setInstanceNodeConfiguration(nodeCfg);
            inmBuilder.setMinionName(minionName);
            inmBuilder.setKey(new Manifest.Key(nodeCfg.uuid + "/" + minionName, Long.toString(latest.get() + 1)));

            imfb.addInstanceNodeManifest(minionName, inmBuilder.insert(hive));
        }

        Key result = imfb.insert(hive);
        InstanceManifest.of(hive, result).getHistory(hive).recordAction(Action.CREATE, context.getUserPrincipal().getName(),
                "Migration to node");

    }

    private void checkAndPrepareInstanceGroupsForMigration(RemoteService minion, MinionMigrationState state) {
        CommonRootResource remoteCrr = ResourceProvider.getVersionedResource(minion, CommonRootResource.class, context);
        CommonRootResource localCrr = rc.initResource(new CommonRootResourceImpl());

        List<InstanceGroupConfiguration> localGroups = localCrr.getInstanceGroups();
        state.allGroups = remoteCrr.getInstanceGroups();

        for (InstanceGroupConfiguration igc : state.allGroups) {
            Optional<InstanceGroupConfiguration> existingIg = localGroups.stream().filter(i -> i.name.equals(igc.name)).findAny();

            if (existingIg.isEmpty()) {
                // need to create it. there is no way to apply the logo, it has to be done manually later
                igc.logo = null;
                localCrr.addInstanceGroup(igc, null);
            }
        }
    }

    private MinionMigrationState checkAndPrepareNodesForMigration(String name, RemoteService minion) {
        BackendInfoResource bir = ResourceProvider.getResource(minion, BackendInfoResource.class, context);
        BackendInfoDto info = bir.getVersion();
        MinionMigrationState result = new MinionMigrationState();

        if (!(info.mode == MinionMode.MANAGED || info.mode == MinionMode.STANDALONE)) {
            throw new WebApplicationException("Requested conversion is not possible, minion has wrong mode: " + info.mode,
                    Status.EXPECTATION_FAILED);
        }

        Map<String, MinionStatusDto> allNodes = bir.getNodeStatus();
        String oldMasterName = null;
        for (Entry<String, MinionStatusDto> entry : allNodes.entrySet()) {
            if (entry.getValue().offline) {
                throw new WebApplicationException(
                        "All nodes on target server must be online during conversion, offline node: " + entry.getKey(),
                        Status.EXPECTATION_FAILED);
            }

            if (entry.getValue().config.master) {
                if (oldMasterName != null) {
                    throw new WebApplicationException("Multiple master nodes found: " + entry.getKey() + ", " + oldMasterName,
                            Status.EXPECTATION_FAILED);
                }

                oldMasterName = entry.getKey();
            } else if (nodes.getAllNodeNames().contains(entry.getKey())) {
                throw new WebApplicationException("Duplicate node name detected, cannot convert: " + entry.getKey(),
                        Status.EXPECTATION_FAILED);
            }
        }

        if (oldMasterName == null) {
            throw new WebApplicationException("No master node configuration found", Status.EXPECTATION_FAILED);
        }

        // update config, this could be a different URL/pack/etc (user provided).
        MinionStatusDto status = allNodes.remove(oldMasterName);
        status.config.remote = minion;
        status.config.master = false;
        status.infoText = "Converting...";
        allNodes.put(name, status);

        result.newName = name;
        result.originalName = oldMasterName;
        result.allNodes = allNodes;

        return result;
    }

    @Override
    public void editNode(String name, RemoteService minion) {
        nodes.editNode(name, minion);
    }

    private Map<String, NodeGroupState> getInstancesToReinstall(String node) {
        Map<String, NodeGroupState> result = new TreeMap<>();

        try (Activity finding = reporter.start("Finding Instances to repair on Node " + node)) {
            CommonRootResource groups = rc.initResource(new CommonRootResourceImpl());

            groups.getInstanceGroups().forEach(g -> {
                NodeGroupState ngs = new NodeGroupState();

                // find all instances in the group
                BHive hive = registry.get(g.name);
                SortedSet<Key> ims = InstanceManifest.scan(hive, true);

                ims.forEach(e -> {
                    InstanceManifest im = InstanceManifest.of(hive, e);
                    InstanceConfiguration i = im.getConfiguration();

                    // find all installed/active versions
                    InstanceStateRecord states = im.getState(hive).read();
                    NodeInstanceState ns = new NodeInstanceState();
                    ns.active = states.activeTag;
                    ns.name = i.name;

                    for (String installed : states.installedTags) {
                        // if it does, we add it to the list we want to re-install.
                        if (im.getInstanceNodeManifests().containsKey(node)) {
                            ns.installed.add(installed);
                        }
                    }

                    if (!ns.installed.isEmpty()) {
                        ngs.instances.put(i.uuid, ns);
                    }
                });

                if (!ngs.instances.isEmpty()) {
                    result.put(g.name, ngs);
                }
            });
        }

        return result;
    }

    @Override
    public void replaceNode(String name, RemoteService minion) {
        // 1. update the node configuration. this also forces contact with the node to be established.
        editNode(name, minion);

        // 3. find all instances in which the node participates.
        Map<String, NodeGroupState> toReinstall = getInstancesToReinstall(name);

        // 4. trigger re-install of all software.
        try (Activity grpReinstall = reporter.start("Replacing Node " + name, toReinstall.size())) {
            for (var entry : toReinstall.entrySet()) {
                String group = entry.getKey();
                NodeGroupState ngs = entry.getValue();

                BHive hive = registry.get(group);

                for (var instanceEntry : ngs.instances.entrySet()) {
                    String instanceId = instanceEntry.getKey();
                    NodeInstanceState nis = instanceEntry.getValue();

                    MasterNamedResource mnr = getNamedMaster(group);

                    try (Activity instReinstall = reporter.start("Restoring " + nis.name, nis.installed.size())) {
                        for (var instanceTag : nis.installed) {
                            InstanceManifest iim = InstanceManifest.load(hive, instanceId, instanceTag);

                            mnr.install(iim.getManifest());
                            if (instanceTag.equals(nis.active)) {
                                mnr.activate(iim.getManifest());
                            }

                            instReinstall.workAndCancelIfRequested(1);
                        }
                    }
                }

                grpReinstall.workAndCancelIfRequested(1);
            }
        }

    }

    @Override
    public void removeNode(String name) {
        nodes.removeNode(name);
    }

    @Override
    public Map<String, String> fsckNode(String name) {
        return nodes.getNodeResourceIfOnlineOrThrow(name, MinionStatusResource.class, context).repairDefaultBHive();
    }

    @Override
    public long pruneNode(String name) {
        return nodes.getNodeResourceIfOnlineOrThrow(name, MinionStatusResource.class, context).pruneDefaultBHive();
    }

    @Override
    public Version getUpdateApiVersion() {
        return UpdateHelper.currentApiVersion();
    }

    @Override
    public void updateV1(Manifest.Key version, boolean clean) {
        BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

        Set<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
        if (!keys.contains(version)) {
            throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
        }

        // find target OS for update package
        OperatingSystem updateOs = getTargetOsFromUpdate(version);

        // Push the update to the nodes. Ensure that master is the last one
        String masterName = root.getState().self;
        Collection<String> nodeNames = nodes.getAllNodeNames();
        SortedMap<String, MinionUpdateResource> toUpdate = new TreeMap<>(new SortOneAsLastComparator(masterName));
        pushUpdate(version, bhive, updateOs, nodeNames, toUpdate);

        // DON'T check for cancel from here on anymore to avoid inconsistent setups
        // (inconsistent setups can STILL occur in mixed-OS setups)
        prepareUpdate(version, clean, toUpdate);

        // now perform the update on all
        List<Throwable> problems = performUpdate(version, toUpdate);
        if (!problems.isEmpty()) {
            WebApplicationException ex = new WebApplicationException("Problem(s) updating minion(s)",
                    Status.INTERNAL_SERVER_ERROR);
            problems.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    @Override
    public void updateNode(String name, Manifest.Key version, boolean clean) {
        BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

        Set<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
        if (!keys.contains(version)) {
            throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
        }

        // find target OS for update package
        OperatingSystem updateOs = getTargetOsFromUpdate(version);

        // Push the update to the nodes. Ensure that master is the last one
        SortedMap<String, MinionUpdateResource> toUpdate = new TreeMap<>();
        pushUpdate(version, bhive, updateOs, Collections.singletonList(name), toUpdate);

        // DON'T check for cancel from here on anymore to avoid inconsistent setups
        // (inconsistent setups can STILL occur in mixed-OS setups)
        prepareUpdate(version, clean, toUpdate);

        // now perform the update on all
        List<Throwable> problems = performUpdate(version, toUpdate);
        if (!problems.isEmpty()) {
            WebApplicationException ex = new WebApplicationException("Problem(s) updating minion(s)",
                    Status.INTERNAL_SERVER_ERROR);
            problems.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    private List<Throwable> performUpdate(Manifest.Key version, SortedMap<String, MinionUpdateResource> toUpdate) {
        List<Throwable> problems = new ArrayList<>();
        toUpdate.entrySet().forEach(entry -> {
            try {
                MinionUpdateResource updateResource = entry.getValue();
                updateResource.update(version); // update schedules and delays, so we have a chance to return this call.
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                log.error("Cannot schedule update on minion: {}", entry.getKey(), e);
                problems.add(e);
            }
        });
        return problems;
    }

    private void prepareUpdate(Manifest.Key version, boolean clean, SortedMap<String, MinionUpdateResource> toUpdate) {
        Activity preparing = reporter.start("Preparing update on Minions...", toUpdate.size());
        // prepare the update on all minions
        for (Map.Entry<String, MinionUpdateResource> ur : toUpdate.entrySet()) {
            try {
                ur.getValue().prepare(version, clean);
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                throw new WebApplicationException("Cannot preapre update on " + ur.getKey(), e);
            }
            preparing.worked(1);
        }
        preparing.done();
    }

    private void pushUpdate(Manifest.Key version, BHive h, OperatingSystem updateOs, Collection<String> nodeNames,
            SortedMap<String, MinionUpdateResource> toUpdate) {
        Activity pushing = reporter.start("Pushing Update to Nodes", nodeNames.size());
        for (String nodeName : nodeNames) {
            MinionDto minionDto = nodes.getNodeConfigIfOnline(nodeName);

            if (minionDto == null) {
                // this means the node needs to be updated separately later on.
                log.warn("Cannot push update to offline node {}", nodeName);
                continue;
            }

            RemoteService service = minionDto.remote;
            try {
                if (minionDto.os == updateOs) {
                    MinionUpdateResource resource = ResourceProvider.getResource(service, MinionUpdateResource.class, context);
                    toUpdate.put(nodeName, resource);
                } else {
                    log.warn("Not updating {}, wrong os ({} != {})", nodeName, minionDto.os, updateOs);
                    pushing.workAndCancelIfRequested(1);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Cannot contact minion: {} - not updating.", nodeName);
                pushing.workAndCancelIfRequested(1);
                continue;
            }

            try {
                h.execute(new PushOperation().addManifest(version).setRemote(service));
            } catch (Exception e) {
                log.error("Cannot push update to minion: {}", nodeName, e);
                throw new WebApplicationException("Cannot push update to minions", e, Status.BAD_GATEWAY);
            }
            pushing.workAndCancelIfRequested(1);
        }
        pushing.done();
    }

    private OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            throw new IllegalStateException("Cannot determin OS from key " + version);
        }

        return scoped.getOperatingSystem();
    }

    @Override
    public MasterNamedResource getNamedMaster(String name) {
        BHive h = registry.get(name);
        if (h == null) {
            throw new WebApplicationException("Hive not found: " + name, Status.NOT_FOUND);
        }

        return rc.initResource(new MasterNamedResourceImpl(root, h, reporter));
    }

    private static final class NodeGroupState {

        public Map<String, NodeInstanceState> instances = new TreeMap<>();
    }

    private static final class NodeInstanceState {

        public String name;
        public List<String> installed = new ArrayList<>();
        public String active;
    }

    private static final class MinionMigrationState {

        public String newName;
        public String originalName;
        public List<InstanceGroupConfiguration> allGroups;
        public Map<String, MinionStatusDto> allNodes;
        public Map<String, Map<String, List<Manifest.Key>>> toFetch = new TreeMap<>();
        public Map<String, List<InstanceDto>> allInstances = new TreeMap<>();
    }

}
