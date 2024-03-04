package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MinionProductUpdatesDto;
import io.bdeploy.interfaces.manifest.managed.MinionUpdateDto;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.actions.ActionBridge;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.BackendInfoDto;
import io.bdeploy.ui.dto.CentralIdentDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.MinionSyncResultDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

public class ManagedServersResourceImpl implements ManagedServersResource {

    private static final Logger log = LoggerFactory.getLogger(ManagedServersResourceImpl.class);

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Inject
    private ProductTransferService transfers;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private Optional<ActionBridge> bridge;

    @Inject
    private ActionFactory af;

    @Inject
    private VersionSorterService vss;

    @Inject
    private TaskSynchronizer tasks;

    @Override
    public void tryAutoAttach(String groupName, ManagedMasterDto target) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(target.uri).build(), target.auth);
        CommonRootResource root = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);

        boolean igExists = false;
        for (InstanceGroupConfiguration cfg : root.getInstanceGroups()) {
            if (cfg.name.equals(groupName)) {
                igExists = true; // don't try to create, instead sync
            }
        }

        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.BAD_REQUEST);
        }

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        InstanceGroupConfiguration igc = igm.read();
        igc.logo = null;

        try {
            // store the attachment locally
            mm.attach(target, false);

            if (!igExists) {
                // initial create without logo - required to create instance group hive.
                root.addInstanceGroup(igc, null);

                // push the latest instance group manifest to the target
                hive.execute(new PushOperation().setHiveName(groupName).addManifest(igm.getKey()).setRemote(svc));
            } else {
                synchronize(groupName, target.hostName);
            }

            ResourceProvider.getVersionedResource(svc, ManagedServersAttachEventResource.class, context)
                    .setLocalAttached(groupName);

            changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
        } catch (Exception e) {
            throw new WebApplicationException("Cannot automatically attach managed server " + target.hostName, e);
        }
    }

    @Override
    public void manualAttach(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.BAD_REQUEST);
        }

        // store the attachment locally
        mm.attach(target, false);

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
    }

    @Override
    public String manualAttachCentral(String central) {
        CentralIdentDto dto = minion.getDecryptedPayload(central, CentralIdentDto.class);

        if (dto.config.name == null) {
            throw new WebApplicationException("Invalid data from central server");
        }

        try (ActionHandle handle = af.run(Actions.ATTACH_TO_CENTRAL, dto.config.name)) {
            RemoteService selfSvc = minion.getSelf();
            RemoteService testSelf = new RemoteService(selfSvc.getUri(), dto.local.auth);

            // will throw if there is an authentication problem.
            InstanceGroupResource self = ResourceProvider.getVersionedResource(testSelf, InstanceGroupResource.class, context);

            dto.config.logo = null; // later.
            self.create(dto.config);
            if (dto.logo != null) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(dto.logo);
                        FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("image", bis)) {
                    self.updateImage(dto.config.name, fdmp);
                } catch (IOException e) {
                    log.error("Failed to update instance group logo", e);
                }
            }
            return dto.config.name;
        }
    }

    @Override
    public String getCentralIdent(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);

        CentralIdentDto dto = new CentralIdentDto();
        dto.config = igm.read();
        dto.local = target;
        if (dto.config.logo != null) {
            try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(dto.config.logo));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                StreamHelper.copy(is, baos);
                dto.logo = baos.toByteArray();
            } catch (IOException e) {
                log.error("Cannot read instance group logo, ignoring", e);
            }
        }

        return minion.getEncryptedPayload(dto);
    }

    @Override
    public List<ManagedMasterDto> getManagedServers(String instanceGroup) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
        return masters.getManagedMasters().values().stream().map(e -> {
            e.auth = null;
            return e;
        }).toList();
    }

    @Override
    public ManagedMasterDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
        InstanceManifest im = InstanceManifest.load(hive, instanceId, instanceTag);

        String selected = new ControllingMaster(hive, im.getManifest()).read().getName();
        if (selected == null) {
            return null;
        }

        ManagedMasterDto dto = masters.getManagedMaster(selected);

        if (dto == null) {
            throw new WebApplicationException("Recorded managed server for instance " + instanceId
                    + " no longer available on instance group: " + instanceGroup, Status.NOT_FOUND);
        }

        // clear token - don't transfer over the wire if not required.
        dto.auth = null;
        return dto;
    }

    private BHive getInstanceGroupHive(String instanceGroup) {
        BHive hive = registry.get(instanceGroup);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + instanceGroup + " locally");
        }
        return hive;
    }

    @Override
    public List<InstanceConfiguration> getInstancesControlledBy(String groupName, String serverName) {
        List<InstanceConfiguration> instances = new ArrayList<>();
        BHive hive = getInstanceGroupHive(groupName);

        SortedSet<Manifest.Key> latestKeys = InstanceManifest.scan(hive, true);

        for (Manifest.Key key : latestKeys) {
            String associated = new ControllingMaster(hive, key).read().getName();
            if (serverName.equals(associated)) {
                instances.add(InstanceManifest.of(hive, key).getConfiguration());
            }
        }
        return instances;
    }

    @Override
    public void deleteManagedServer(String groupName, String serverName) {
        try (ActionHandle h = af.run(Actions.REMOVE_MANAGED, groupName, null, serverName)) {
            BHive hive = getInstanceGroupHive(groupName);
            List<InstanceConfiguration> controlled = getInstancesControlledBy(groupName, serverName);

            // delete all of the instances /LOCALLY/ on the central, but NOT using the remote master (we "just" detach).
            for (InstanceConfiguration cfg : controlled) {
                Set<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(cfg.id));
                allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
            }

            new ManagedMasters(hive).detach(serverName);

            InstanceGroupManifest igm = new InstanceGroupManifest(hive);
            changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
        }
    }

    @Override
    public void updateManagedServer(String groupName, String serverName, ManagedMasterDto update) {
        if (!serverName.equals(update.hostName)) {
            throw new WebApplicationException("Server name does not match configuration: " + serverName, Status.BAD_REQUEST);
        }

        ManagedMasters mm = new ManagedMasters(getInstanceGroupHive(groupName));

        ManagedMasterDto old = mm.read().getManagedMaster(serverName);
        if (old == null) {
            throw new WebApplicationException("Server does not (yet) exist: " + serverName, Status.NOT_FOUND);
        }

        if (update.auth == null || update.auth.isBlank()) {
            // token might have been cleared out.
            update.auth = old.auth;
        }

        mm.attach(update, true);

        BHive hive = getInstanceGroupHive(groupName);
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
    }

    @Override
    public Map<String, MinionStatusDto> getMinionStateOfManagedServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNodes();
    }

    private RemoteService getConfiguredRemote(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        ManagedMasterDto attached = new ManagedMasters(hive).read().getManagedMaster(serverName);
        if (attached == null) {
            throw new WebApplicationException("Managed server " + serverName + " not found for instance group " + groupName,
                    Status.EXPECTATION_FAILED);
        }

        return new RemoteService(UriBuilder.fromUri(attached.uri).build(), attached.auth);
    }

    @Override
    public MinionSyncResultDto synchronize(String groupName, String serverName) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return null;
        }
        return tasks.perform("Sync-" + groupName + "-" + serverName, () -> {
            try (ActionHandle h = af.run(Actions.SYNCHRONIZING, groupName, null, serverName)) {
                BHive hive = getInstanceGroupHive(groupName);
                try (Transaction t = hive.getTransactions().begin()) {
                    return synchronizeTransacted(hive, groupName, serverName);
                } catch (Exception e) {
                    log.warn("Cannot synchronize {}: {}", serverName, e.toString());
                    if (log.isDebugEnabled()) {
                        log.debug("Error:", e);
                    }

                    // in case we have a dedicated status associated.
                    if (e instanceof WebApplicationException) {
                        throw e;
                    }

                    throw new WebApplicationException("Cannot synchronize " + serverName, e);
                }
            }
        });
    }

    private MinionSyncResultDto synchronizeTransacted(BHive hive, String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);

        BackendInfoResource backendInfo = ResourceProvider.getVersionedResource(svc, BackendInfoResource.class, context);
        BackendInfoDto infoDto = backendInfo.getVersion();
        if (infoDto.mode != MinionMode.MANAGED) {
            throw new WebApplicationException("Server is no longer in managed mode: " + serverName, Status.SERVICE_UNAVAILABLE);
        }

        if (infoDto.isInitialConnectionCheckFailed) {
            throw new WebApplicationException(
                    "Server has internal connection issues. Please check on the target server: " + serverName,
                    Status.SERVICE_UNAVAILABLE);
        }

        MinionSyncResultDto result = new MinionSyncResultDto();
        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);

        List<InstanceManifest> removedInstances = new ArrayList<>();
        List<SystemManifest> removedSystems = new ArrayList<>();

        // 1. Fetch information about updates, possibly required
        attached.update = getUpdates(svc);

        // don't continue actual data sync if update MUST be installed.
        if (!attached.update.forceUpdate) {
            // notify the action bridge service. it must exist since we're on central.
            if (bridge.isPresent()) {
                bridge.get().onSync(serverName, svc);
            } else {
                // this should never happen. central (and only central) must have it registered!
                log.error("Action Bridge not available!");
            }

            // 2. Sync instance group data with managed server.
            CommonRootResource root = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);
            if (root.getInstanceGroups().stream().map(g -> g.name).noneMatch(n -> n.equals(groupName))) {
                throw new WebApplicationException("Instance group (no longer?) found on the managed server", Status.NOT_FOUND);
            }

            Manifest.Key igKey = igm.getKey();
            String attributesMetaName = igm.getAttributes(hive).getMetaManifest().getMetaName();
            Set<Key> metaManifests = hive.execute(new ManifestListOperation().setManifestName(attributesMetaName));

            PushOperation push = new PushOperation().addManifest(igKey).setRemote(svc).setHiveName(groupName);
            metaManifests.forEach(push::addManifest);
            hive.execute(push);

            // 3a. on newer versions, trigger update of overall status.
            try {
                MasterRootResource mr = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);

                // this trigger will update the current information on each instance and persist that in a meta-manifest,
                // which then in turn is fetched later.
                mr.getNamedMaster(groupName).updateOverallStatus();
            } catch (Exception e) {
                log.info("Cannot update overall instance status before sync: {}", e.toString());
            }

            // 3b. Fetch all instance and meta manifests, no products.
            CommonRootResource masterRoot = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);
            CommonInstanceResource master = masterRoot.getInstanceResource(groupName);
            SortedMap<Key, InstanceConfiguration> instances = master.listInstanceConfigurations(true);
            List<String> instanceIds = instances.values().stream().map(ic -> ic.id).toList();

            FetchOperation fetchOp = new FetchOperation().setRemote(svc).setHiveName(groupName);
            try (RemoteBHive rbh = RemoteBHive.forService(svc, groupName, new ActivityReporter.Null())) {
                Set<Manifest.Key> keysToFetch = new LinkedHashSet<>();

                // maybe we can scope this down a little in the future.
                rbh.getManifestInventory(instanceIds.toArray(String[]::new)).forEach((k, v) -> keysToFetch.add(k));

                // we're also interested in all the related meta manifests.
                rbh.getManifestInventory(instanceIds.stream().map(s -> MetaManifest.META_PREFIX + s).toArray(String[]::new))
                        .forEach((k, v) -> keysToFetch.add(k));

                // set calculated keys to fetch operation.
                fetchOp.addManifest(keysToFetch);
            }

            // 3c. Fetch all systems on the server. Systems which are in *use* by an instance would be fetched
            // automatically, but we want *all* systems, even empty ones.
            Set<String> systemIds = new TreeSet<>();
            Set<Key> systems = new TreeSet<>();
            try {
                MasterSystemResource msr = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context)
                        .getNamedMaster(groupName).getSystemResource();

                msr.list().forEach((k, v) -> {
                    systems.add(k);
                    systemIds.add(v.id);
                });
                fetchOp.addManifest(systems);
            } catch (Exception e) {
                log.info("Cannot fetch systems from {}: {}", serverName, e.toString());
            }

            hive.execute(fetchOp);

            // 4. Remove local instances no longer available on the remote
            SortedSet<Key> keysOnCentral = InstanceManifest.scan(hive, true);
            for (Key key : keysOnCentral) {
                InstanceManifest im = InstanceManifest.of(hive, key);
                if (instanceIds.contains(im.getConfiguration().id)) {
                    // MAYBE has been updated by the sync.
                    continue; // OK. instance exists
                }

                if (!serverName.equals(new ControllingMaster(hive, key).read().getName())) {
                    continue; // OK. other server or null (should not happen).
                }

                // Not OK: instance no longer on server.
                Set<Key> allInstanceMfs = hive.execute(new ManifestListOperation().setManifestName(im.getConfiguration().id));
                allInstanceMfs.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
                removedInstances.add(im);
            }

            // 5 Remove local systems no longer available on the remote
            SortedSet<Key> systemsOnCentral = SystemManifest.scan(hive);
            for (Key k : systemsOnCentral) {
                SystemManifest sm = SystemManifest.of(hive, k);
                if (systemIds.contains(sm.getConfiguration().id)) {
                    continue; // OK. system exists.
                }

                if (!serverName.equals(new ControllingMaster(hive, k).read().getName())) {
                    continue; // OK. other server.
                }

                Set<Key> allSystemMfs = hive.execute(new ManifestListOperation().setManifestName(sm.getKey().getName())); // all versions
                allSystemMfs.forEach(s -> hive.execute(new ManifestDeleteOperation().setToDelete(s)));
                removedSystems.add(sm);
            }

            // 6. for all the fetched manifests, if they are instances, associate the server with it, and send out a change
            for (Manifest.Key instance : instances.keySet()) {
                new ControllingMaster(hive, instance).associate(serverName);

                try {
                    // additionally also read the last known instance overall state and return it...
                    InstanceManifest im = InstanceManifest.of(hive, instance);
                    result.states.add(new InstanceOverallStatusDto(im.getConfiguration().id, im.getOverallState(hive).read()));
                } catch (Exception e) {
                    // this is ignorable.
                    log.error("Cannot read instance overall state for {}: {}", instance, e.toString());
                }
            }

            // 7. repeat for all systems which should also be associated with a server.
            for (Manifest.Key system : systems) {
                new ControllingMaster(hive, system).associate(serverName);
            }

            // 8. try to sync instance group properties
            try {
                MasterSettingsResource msr = ResourceProvider.getVersionedResource(svc, MasterSettingsResource.class, context);
                msr.mergeInstanceGroupAttributesDescriptors(minion.getSettings().instanceGroup.attributes);
            } catch (Exception e) {
                log.warn("Cannot sync InstanceGroup properties to managed server, ignoring", e);
            }

            attached.lastSync = Instant.now();
        }

        // 9. Fetch minion information and store in the managed masters
        Map<String, MinionStatusDto> status = backendInfo.getNodeStatus();
        Map<String, MinionDto> config = status.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().config));
        attached.minions = new MinionConfiguration(config);

        // 10. Check if managed server has newer products that central
        MinionProductUpdatesDto productUpdatesDto = new MinionProductUpdatesDto();
        productUpdatesDto.newerVersionAvailable = new HashMap<>();
        SortedSet<Key> productsOnCentral = ProductManifest.scan(hive);
        List<ProductDto> productsOnManaged = listProducts(groupName, serverName);
        Map<String, Comparator<String>> comparators = new TreeMap<>();
        for (ProductDto product : productsOnManaged) {
            String productName = product.key.getName();
            String productTag = product.key.getTag();

            if (productUpdatesDto.newerVersionAvailable.containsKey(productName)) {
                continue;
            }

            Comparator<String> productVersionComparator = comparators.computeIfAbsent(product.key.getName(),
                    k -> vss.getTagComparator(groupName, product.key));

            boolean allCentralVersionsAreOlder = productsOnCentral.stream().filter(key -> key.getName().equals(productName))
                    .map(Key::getTag).allMatch(tag -> productVersionComparator.compare(tag, productTag) == -1);
            if (allCentralVersionsAreOlder) {
                productUpdatesDto.newerVersionAvailable.put(productName, true);
            }
        }

        attached.productUpdates = productUpdatesDto;

        // 11. update current information in the hive.
        mm.attach(attached, true);

        // 12. send out notifications after *all* is done.
        for (var im : removedInstances) {
            changes.remove(ObjectChangeType.INSTANCE, im.getManifest(), new ObjectScope(groupName));
        }
        for (var sm : removedSystems) {
            changes.remove(ObjectChangeType.SYSTEM, sm.getKey(), new ObjectScope(groupName));
        }

        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));

        result.server = attached;

        return result;
    }

    @Override
    public List<ProductDto> listProducts(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        return ResourceProvider.getVersionedResource(svc, InstanceGroupResource.class, context).getProductResource(groupName)
                .list(null);
    }

    @Override
    public void transferProducts(String groupName, ProductTransferDto transfer) {
        transfers.initTransfer(getInstanceGroupHive(groupName), groupName, transfer);
    }

    @Override
    public SortedSet<ProductDto> getActiveTransfers(String groupName) {
        return transfers.getActiveTransfers(groupName);
    }

    private MinionUpdateDto getUpdates(RemoteService svc) {
        CommonRootResource root = ResourceProvider.getResource(svc, CommonRootResource.class, context);
        Version managedVersion = root.getVersion();
        Version runningVersion = VersionHelper.getVersion();

        // Determine whether or not an update must be installed
        MinionUpdateDto updateDto = new MinionUpdateDto();
        updateDto.updateVersion = runningVersion;
        updateDto.runningVersion = managedVersion;
        updateDto.updateAvailable = VersionHelper.compare(runningVersion, managedVersion) > 0;
        updateDto.forceUpdate = runningVersion.getMajor() > managedVersion.getMajor();

        if (managedVersion.getMajor() == 5 || managedVersion.getMajor() == 6) {
            // from >=5.x -> 6.x no forced update, data format is unchanged, major version due to activities/actions ("visuals" only).
            // from >=5.x -> 7.x no forced update. Older servers will not understand our data format (uuid/uid -> id), but >=5.x will.
            updateDto.forceUpdate = false;
        }

        // Contact the remote service to find out all installed versions
        Set<ScopedManifestKey> remoteVersions = new HashSet<>();
        try (RemoteBHive rbh = RemoteBHive.forService(svc, null, new ActivityReporter.Null())) {
            SortedMap<Key, ObjectId> inventory = rbh.getManifestInventory(SoftwareUpdateResource.BDEPLOY_MF_NAME,
                    SoftwareUpdateResource.LAUNCHER_MF_NAME);
            inventory.keySet().stream().forEach(key -> remoteVersions.add(ScopedManifestKey.parse(key)));
        }

        // Determine what is available in our hive
        Set<ScopedManifestKey> localVersion = new HashSet<>();
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.BDEPLOY_MF_NAME));
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.LAUNCHER_MF_NAME));

        // Compute what is missing and what needs to be installed
        updateDto.packagesToInstall = localVersion.stream().map(ScopedManifestKey::getKey).toList();
        localVersion.removeAll(remoteVersions);
        updateDto.packagesToTransfer = localVersion.stream().map(ScopedManifestKey::getKey).toList();

        return updateDto;
    }

    @Override
    public void transferUpdate(String groupName, String serverName, MinionUpdateDto updates) {
        try (ActionHandle h = af.run(Actions.MANAGED_UPDATE_TRANSFER, groupName, null, serverName)) {
            // Avoid pushing all manifest if we do not specify what to transfer
            if (updates.packagesToTransfer.isEmpty()) {
                return;
            }

            RemoteService svc = getConfiguredRemote(groupName, serverName);

            // Push them to the default hive of the target server
            PushOperation push = new PushOperation();
            updates.packagesToTransfer.forEach(push::addManifest);

            // Updates are stored in the local default hive
            BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
            defaultHive.execute(push.setRemote(svc));

            // update the information in the hive.
            BHive hive = getInstanceGroupHive(groupName);
            ManagedMasters mm = new ManagedMasters(hive);
            ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
            attached.update = getUpdates(svc);
            mm.attach(attached, true);
        }
    }

    @Override
    public void installUpdate(String groupName, String serverName, MinionUpdateDto updates) {
        try (ActionHandle h = af.run(Actions.MANAGED_UPDATE_INSTALL, groupName, null, serverName)) {
            // Only retain server packages in the list of packages to install
            Collection<Key> keys = updates.packagesToInstall;
            Collection<Key> server = keys.stream().filter(UpdateHelper::isBDeployServerKey).toList();

            BHive hive = getInstanceGroupHive(groupName);

            ManagedMasters mm = new ManagedMasters(hive);
            ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
            Map<String, MinionDto> allMinions = attached.minions.values();

            // Determine OS of the master
            Optional<MinionDto> masterDto = allMinions.values().stream().filter(dto -> dto.master).findFirst();
            if (!masterDto.isPresent()) {
                throw new WebApplicationException("Cannot determine master node");
            }

            // Trigger the update on the master node
            RemoteService svc = getConfiguredRemote(groupName, serverName);
            UpdateHelper.update(svc, server, true, context);

            // update the information in the hive.
            attached.update = getUpdates(svc);
            mm.attach(attached, true);

            // force a new RemoteService instance on next call
            JerseyClientFactory.invalidateCached(svc);
        }
    }

    @Override
    public Version pingServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        try {
            BackendInfoResource info = ResourceProvider.getVersionedResource(svc, BackendInfoResource.class, null);
            return info.getVersion().version;
        } catch (Exception e) {
            throw new WebApplicationException("Cannot contact " + serverName, e, Status.GATEWAY_TIMEOUT);
        }
    }

    private Collection<ScopedManifestKey> getLocalPackage(String manifestName) {
        String runningVersion = VersionHelper.getVersion().toString();

        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        ManifestListOperation operation = new ManifestListOperation().setManifestName(manifestName);
        Set<Key> result = defaultHive.execute(operation);
        return result.stream().map(ScopedManifestKey::parse).filter(smk -> smk.getTag().equals(runningVersion))
                .collect(Collectors.toSet());
    }

}
