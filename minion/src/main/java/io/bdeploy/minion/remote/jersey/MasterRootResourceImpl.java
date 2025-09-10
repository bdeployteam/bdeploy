package io.bdeploy.minion.remote.jersey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import io.bdeploy.common.RetryableScope;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.SortOneAsLastComparator;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionDto.MinionNodeType;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.MinionUpdateResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.SoftwareUpdateResource;
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
    private NodeManager nodes;

    @Context
    private ResourceContext rc;

    @Context
    private SecurityContext context;

    @Inject
    private RequestScopedParallelOperationsService rspos;

    @Inject
    private ActionFactory af;

    @Override
    public Map<String, MinionStatusDto> getNodes() {
        return nodes.getAllNodeStatus();
    }

    @Override
    public void addServerNode(String name, RemoteService minion) {
        try (var handle = af.run(Actions.ADD_NODE, null, null, name)) {
            nodes.addNode(name, MinionDto.create(false, minion, MinionNodeType.SERVER));
        }
    }

    @Override
    public void convertNode(String name, RemoteService minion) {
        try (var handle = af.run(Actions.CONVERT_TO_NODE, null, null, name)) {
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

        for (InstanceGroupConfiguration igc : state.allGroups) {
            InstanceResource remoteIr = remoteIgr.getInstanceResource(igc.name);

            List<InstanceDto> instances = remoteIr.list();
            state.allInstances.put(igc.name, instances);
            Map<String, List<Manifest.Key>> toFetch = state.toFetch.computeIfAbsent(igc.name, k -> new TreeMap<>());

            for (InstanceDto instance : instances) {
                List<Manifest.Key> toFetchForInstance = new ArrayList<>();
                toFetchForInstance.add(instance.activeVersion != null ? instance.activeVersion : instance.latestVersion);
                toFetchForInstance
                        .add(instance.activeProduct != null ? instance.activeProduct : instance.instanceConfiguration.product);
                toFetch.put(instance.instanceConfiguration.id, toFetchForInstance);
            }
        }
    }

    private void transferAndProcessInstances(RemoteService minion, MinionMigrationState state) {
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
                    grpHive.execute(new FetchOperation().setRemote(minion).setHiveName(grpEntry.getKey()).addManifest(toFetch));

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
        for (Map.Entry<String, InstanceNodeConfiguration> node : existing.getInstanceNodeConfigurations(hive).entrySet()) {
            InstanceNodeManifest.Builder inmBuilder = new InstanceNodeManifest.Builder();
            InstanceNodeConfiguration nodeCfg = node.getValue();

            String minionName = node.getKey();
            if (minionName.equals(oldNodeName)) {
                minionName = newNodeName;
            }

            inmBuilder.addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME, existing.getConfiguration().configTree);
            inmBuilder.setInstanceNodeConfiguration(nodeCfg);
            inmBuilder.setMinionName(minionName);
            inmBuilder.setKey(new Manifest.Key(nodeCfg.id + "/" + minionName, Long.toString(latest.get() + 1)));

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

        if (!(info.mode == MinionMode.MANAGED || info.mode == MinionMode.STANDALONE)) {
            throw new WebApplicationException("Requested conversion is not possible, minion has wrong mode: " + info.mode,
                    Status.EXPECTATION_FAILED);
        }

        BackendInfoResource newBir = updatedIfNeeded(minion, info);
        bir = newBir != null ? newBir : bir;

        boolean awaitedNodes = false;
        Map<String, MinionStatusDto> allNodes = null;
        outerCheckLoop: for (int i = 0; i < 100; ++i) {
            allNodes = bir.getNodeStatus();

            for (Entry<String, MinionStatusDto> entry : allNodes.entrySet()) {
                if (entry.getValue().offline) {
                    // this one is offline, we need to wait a little more.
                    waitASecond();
                    continue outerCheckLoop;
                }
            }

            awaitedNodes = true; // all online! :)
            break;
        }

        if (allNodes == null || !awaitedNodes) {
            throw new WebApplicationException("All nodes must be online during migration, failed to await online state.",
                    Status.EXPECTATION_FAILED);
        }

        String oldMasterName = null;
        for (Entry<String, MinionStatusDto> entry : allNodes.entrySet()) {
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

        if (!oldMasterName.equals(name) && allNodes.containsKey(name)) {
            // we change the master name, but unfortunately to something that we already have in the nodes... ups.
            throw new WebApplicationException("New provided master name is already used by one of its nodes: " + name,
                    Status.EXPECTATION_FAILED);
        }

        // update config, this could be a different URL/pack/etc (user provided).
        MinionStatusDto status = allNodes.remove(oldMasterName);
        status.config.remote = minion;
        status.config.master = false;
        status.infoText = "Converting...";
        allNodes.put(name, status);

        MinionMigrationState result = new MinionMigrationState();
        result.newName = name;
        result.originalName = oldMasterName;
        result.allNodes = allNodes;
        return result;
    }

    private BackendInfoResource updatedIfNeeded(RemoteService minion, BackendInfoDto info) {
        // update the target server to the same BDeploy version as we have running. we do this before conversion,
        // so the server is responsible for updating individual nodes as well.
        // NOTE: we do not need to push launchers, as they will not be required on nodes.
        List<Manifest.Key> targets = getSystemManifest(SoftwareUpdateResource.BDEPLOY_MF_NAME).stream()
                .map(ScopedManifestKey::getKey).toList();

        // not possible to update if we do not have any update locally present to push.
        if (targets.isEmpty() || VersionHelper.compare(info.version, VersionHelper.getVersion()) == 0) {
            return null;
        }

        log.info("Performing update to align BDeploy version ({} != {})", info.version, VersionHelper.getVersion());

        // Push them to the default hive of the target server
        PushOperation push = new PushOperation();
        targets.forEach(push::addManifest);

        // Updates are stored in the local default hive
        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        defaultHive.execute(push.setRemote(minion));

        // do it.
        UpdateHelper.update(minion, targets, true, context);

        // after the update, the server takes a second until it *begins* to perform a restart.
        waitASecond();

        // force a new RemoteService instance on next call
        JerseyClientFactory.invalidateCached(minion);

        // now wait for the server to be back up, max 100 seconds.
        try {
            BackendInfoResource newbir = ResourceProvider.getResource(minion, BackendInfoResource.class, context);
            RetryableScope.create().withMaxRetries(100).withDelay(1_000).run(() -> {
                BackendInfoDto newinfo = newbir.getVersion();

                if (VersionHelper.compare(newinfo.version, VersionHelper.getVersion()) == 0) {
                    return; // we made it.
                }

                throw new IllegalStateException("Target Server did not update to version " + VersionHelper.getVersion()
                        + ", still has " + newinfo.version);
            });
            return newbir; // server is there, use the new resource.
        } catch (Exception e) {
            throw new WebApplicationException("Cannot await update of target server(s) before migration.", e,
                    Status.EXPECTATION_FAILED);
        }
    }

    private static void waitASecond() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebApplicationException("Cannot await update on target server", Status.EXPECTATION_FAILED);
        }
    }

    private Collection<ScopedManifestKey> getSystemManifest(String manifestName) {
        String runningVersion = VersionHelper.getVersion().toString();

        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        ManifestListOperation operation = new ManifestListOperation().setManifestName(manifestName);
        Set<Key> result = defaultHive.execute(operation);
        return result.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull)
                .filter(smk -> smk.getTag().equals(runningVersion)).collect(Collectors.toSet());
    }

    @Override
    public void editNode(String name, RemoteService minion) {
        try (var handle = af.run(Actions.EDIT_NODE, null, null, name)) {
            nodes.editNode(name, minion);
        }
    }

    @Override
    public void replaceNode(String name, RemoteService minion) {
        try (var handle = af.run(Actions.REPLACE_NODE, null, null, name)) {
            // 1. update the node configuration. this also forces contact with the node to be established.
            editNode(name, minion);

            // And now we're done, as the node synchronizer will take care of the rest asynchronously.
        }
    }

    @Override
    public void removeNode(String name) {
        try (var handle = af.run(Actions.REMOVE_NODE, null, null, name)) {
            nodes.removeNode(name);
        }
    }

    @Override
    public Map<String, String> fsckNode(String name) {
        try (var handle = af.run(Actions.FSCK_NODE, null, null, name)) {
            return nodes.getNodeResourceIfOnlineOrThrow(name, MinionStatusResource.class, context).repairDefaultBHive();
        }
    }

    @Override
    public long pruneNode(String name) {
        try (var handle = af.run(Actions.PRUNE_NODE, null, null, name)) {
            return nodes.getNodeResourceIfOnlineOrThrow(name, MinionStatusResource.class, context).pruneDefaultBHive();
        }
    }

    @Override
    public void restartNode(String name) {
        try (var handle = af.run(Actions.RESTART_NODE, null, null, name)) {
            nodes.getNodeResourceIfOnlineOrThrow(name, MinionUpdateResource.class, context).restart();
        }
    }

    @Override
    public void shutdownNode(String name) {
        try (var handle = af.run(Actions.SHUTDOWN_NODE, null, null, name)) {
            nodes.getNodeResourceIfOnlineOrThrow(name, MinionUpdateResource.class, context).shutdown();
        }
    }

    @Override
    public Version getUpdateApiVersion() {
        return UpdateHelper.currentApiVersion();
    }

    @Override
    public void updateV1(Manifest.Key version, boolean clean) {
        try (var handle = af.run(Actions.UPDATE, null, null, version.getTag())) {
            BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

            Set<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
            if (!keys.contains(version)) {
                throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
            }

            // find target OS for update package
            OperatingSystem updateOs = getTargetOsFromUpdate(version);

            // Push the update to the nodes. Ensure that master is the last one

            Collection<String> nodeNames = nodes.getAllNodeNames();
            SortedMap<String, MinionUpdateResource> toUpdate = pushUpdate(version, bhive, updateOs, nodeNames);

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
    }

    @Override
    public void updateNode(String name, Manifest.Key version, boolean clean) {
        try (var handle = af.run(Actions.UPDATE_NODE, null, null, name)) {
            BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

            Set<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
            if (!keys.contains(version)) {
                throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
            }

            // find target OS for update package
            OperatingSystem updateOs = getTargetOsFromUpdate(version);

            // Push the update to the nodes. Ensure that master is the last one
            SortedMap<String, MinionUpdateResource> toUpdate = pushUpdate(version, bhive, updateOs,
                    Collections.singletonList(name));

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
    }

    private static List<Throwable> performUpdate(Manifest.Key version, SortedMap<String, MinionUpdateResource> toUpdate) {
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
        List<Runnable> runnables = new ArrayList<>();

        // prepare the update on all minions
        for (Map.Entry<String, MinionUpdateResource> ur : toUpdate.entrySet()) {
            runnables.add(() -> {
                try {
                    ur.getValue().prepare(version, clean);
                } catch (Exception e) {
                    // don't immediately throw to update as many minions as possible.
                    // this Exception should actually never happen according to the contract.
                    throw new WebApplicationException("Cannot preapre update on " + ur.getKey(), e);
                }
            });
        }

        rspos.runAndAwaitAll("Prepare-Update", runnables, null);
    }

    private SortedMap<String, MinionUpdateResource> pushUpdate(Manifest.Key version, BHive h, OperatingSystem updateOs,
            Collection<String> nodeNames) {
        String masterName = root.getState().self;

        List<Runnable> runnables = new ArrayList<>();
        SortedMap<String, MinionUpdateResource> toUpdate = new TreeMap<>(new SortOneAsLastComparator(masterName));

        for (String nodeName : nodeNames) {
            MinionDto minionDto = nodes.getNodeConfigIfOnline(nodeName);

            if (minionDto == null) {
                // this means the node needs to be updated separately later on.
                log.warn("Cannot push update to offline node {}", nodeName);
                continue;
            }

            runnables.add(() -> {
                RemoteService service = minionDto.remote;
                try {
                    if (minionDto.os == updateOs) {
                        MinionUpdateResource resource = ResourceProvider.getResource(service, MinionUpdateResource.class,
                                context);
                        synchronized (toUpdate) {
                            toUpdate.put(nodeName, resource);
                        }
                    } else {
                        log.warn("Not updating {}, wrong os ({} != {})", nodeName, minionDto.os, updateOs);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Cannot contact minion: {} - not updating.", nodeName, e);
                    return;
                }

                try {
                    h.execute(new PushOperation().addManifest(version).setRemote(service));
                } catch (Exception e) {
                    log.error("Cannot push update to minion: {}", nodeName, e);
                    throw new WebApplicationException("Cannot push update to minions", e, Status.BAD_GATEWAY);
                }
            });
        }

        rspos.runAndAwaitAll("Push-Update", runnables, h.getTransactions());

        return toUpdate;
    }

    private static OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            log.warn("Cannot determine OS from key {}", version);
            return OperatingSystem.UNKNOWN;
        }

        return scoped.getOperatingSystem();
    }

    @Override
    public void restartServer() {
        // never-ending restart-server action which will notify the web-ui of pending restart.
        af.run(Actions.RESTART_SERVER);
        root.getServerProcessManager().performRestart(1_000);
    }

    @Override
    public void createStackDump() {
        Threads.dump(root.getLogDir(), "Running-Threads.dump");
    }

    @Override
    public MasterNamedResource getNamedMaster(String name) {
        BHive h = registry.get(name);
        if (h == null) {
            throw new WebApplicationException("Hive not found: " + name, Status.NOT_FOUND);
        }

        return rc.initResource(new MasterNamedResourceImpl(root, h, name));
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
