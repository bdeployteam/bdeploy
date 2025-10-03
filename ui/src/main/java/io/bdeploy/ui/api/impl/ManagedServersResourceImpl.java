package io.bdeploy.ui.api.impl;

import static io.bdeploy.interfaces.remote.versioning.VersionMismatchFilter.CODE_VERSION_MISMATCH;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.actions.ActionBridge;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.GroupLockService;
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

    @Inject
    private GroupLockService gls;

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
            // also clear auth of all nodes.
            e.minions.minionMap().values().forEach(v -> v.clearAuthInformation());
            return e;
        }).toList();
    }

    @Override
    public ManagedMasterDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = getInstanceGroupHive(instanceGroup);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, instanceTag);
        String selected = new ControllingMaster(hive, im.getKey()).read().getName();
        if (selected == null) {
            return null;
        }

        ManagedMasterDto dto = new ManagedMasters(hive).read().getManagedMaster(selected);
        if (dto == null) {
            throw new WebApplicationException("Recorded managed server for instance " + instanceId
                    + " no longer available on instance group: " + instanceGroup, Status.NOT_FOUND);
        }

        // clear token - don't transfer over the wire if not required.
        dto.auth = null;
        dto.minions.minionMap().values().forEach(v -> v.clearAuthInformation());
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

    private Map<Manifest.Key, String> getSystemsControlledBy(String groupName, String serverName) {
        Map<Manifest.Key, String> systems = new HashMap<>();
        BHive hive = getInstanceGroupHive(groupName);

        SortedSet<Manifest.Key> latestKeys = SystemManifest.scan(hive);

        for (Manifest.Key key : latestKeys) {
            String associated = new ControllingMaster(hive, key).read().getName();
            if (serverName.equals(associated)) {
                systems.put(key, SystemManifest.of(hive, key).getConfiguration().id);
            }
        }
        return systems;
    }

    @Override
    public void deleteManagedServer(String groupName, String serverName) {
        try (ActionHandle h = af.run(Actions.REMOVE_MANAGED, groupName, null, serverName)) {
            BHive hive = getInstanceGroupHive(groupName);
            List<InstanceConfiguration> controlled = getInstancesControlledBy(groupName, serverName);

            for (InstanceConfiguration cfg : controlled) {
                // delete all of the instances /LOCALLY/ on the central, but NOT using the remote master (we "just" detach)
                Set<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(cfg.id));
                allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
            }

            getSystemsControlledBy(groupName, serverName).forEach((key, id) -> {
                // delete all of the systems /LOCALLY/ on the central, but NOT using the remote master (we "just" detach)
                SystemManifest.delete(hive, id);
                changes.remove(ObjectChangeType.SYSTEM, key, new ObjectScope(groupName));
            });

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
            var lock = gls.getLock(groupName);
            lock.writeLock().lock();
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
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private MinionSyncResultDto synchronizeTransacted(BHive centralHive, String groupName, String managedServerName) {
        RemoteService managedRemote = getConfiguredRemote(groupName, managedServerName);

        BackendInfoResource managedBackendInfo = ResourceProvider.getVersionedResource(managedRemote, BackendInfoResource.class,
                context);
        BackendInfoDto managedInfoDto = managedBackendInfo.getVersion();
        if (managedInfoDto.mode != MinionMode.MANAGED) {
            throw new WebApplicationException("Server is no longer in managed mode: " + managedServerName,
                    Status.SERVICE_UNAVAILABLE);
        }

        if (managedInfoDto.isInitialConnectionCheckFailed) {
            throw new WebApplicationException(
                    "Server has internal connection issues. Please check on the target server: " + managedServerName,
                    Status.SERVICE_UNAVAILABLE);
        }

        MinionSyncResultDto result = new MinionSyncResultDto();
        ManagedMasters managedMasters = new ManagedMasters(centralHive);
        ManagedMasterDto managedMasterDto = managedMasters.read().getManagedMaster(managedServerName);
        InstanceGroupManifest centralIgm = new InstanceGroupManifest(centralHive);

        List<Key> removedInstances = new ArrayList<>();
        List<Key> removedSystems = new ArrayList<>();

        // 1. Fetch information about updates, possibly required
        managedMasterDto.update = getUpdates(managedRemote);

        // Skip actual data sync if update MUST be installed
        if (!managedMasterDto.update.forceUpdate) {
            // Notify the action bridge service -> it must exist since we're on central
            if (bridge.isPresent()) {
                bridge.get().onSync(managedServerName, managedRemote);
            } else {
                // This should never happen. central (and only central) must have it registered
                log.error("Action Bridge not available!");
            }

            // 2. Sync instance group data with managed server
            CommonRootResource managedCommonRoot = ResourceProvider.getVersionedResource(managedRemote, CommonRootResource.class,
                    context);
            syncInstanceGroup(managedRemote, managedCommonRoot, groupName, centralHive, centralIgm);

            // 3. On newer versions, trigger update of overall status
            try {
                MasterRootResource managedMasterRoot = ResourceProvider.getVersionedResource(managedRemote,
                        MasterRootResource.class, context);
                managedMasterRoot.getNamedMaster(groupName).updateOverallStatus();
            } catch (Exception e) {
                log.info("Cannot update overall instance status before sync: {}", e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception", e);
                }
            }

            // 4. Fetch all instance, meta manifests and systems from the managed server and push them into the central BHive.
            CommonInstanceResource instanceResource = managedCommonRoot.getInstanceResource(groupName);
            Set<Manifest.Key> instanceKeys;
            try {
                // preferred for >7.2.0: only fetch the keys, skip all the configuration contents.
                instanceKeys = instanceResource.listInstanceKeys(false);
            } catch (WebApplicationException ex) {
                if (ex.getResponse().getStatus() != CODE_VERSION_MISMATCH) {
                    throw ex;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Falling back to fetching complete configuration from {} in {}", managedServerName, groupName);
                }

                // fallback: in case the key only method is not yet available on the managed server.
                instanceKeys = instanceResource.listInstanceConfigurations(false).keySet();
            }
            Set<Key> managedSystems = new TreeSet<>();
            Set<String> managedSystemIds = new TreeSet<>();
            syncAddInstancesAndSystems(managedRemote, groupName, managedServerName, centralHive,
                    instanceKeys.stream().map(InstanceManifest::getIdFromKey).toList(), managedSystems, managedSystemIds);

            // 5. Determine which of the instances and systems of the central server no longer exist on the managed server and delete them.
            syncRemoveInstancesAndSystems(managedServerName, centralHive, instanceKeys, managedSystemIds, removedInstances,
                    removedSystems);

            // from here on we only want the LATEST key for each instance. The map contains the name of the key for uniqueness along with
            // the key which has the highest numeric tag.
            Map<String, Manifest.Key> latestByName = new TreeMap<>();
            for (Manifest.Key instanceKey : instanceKeys) {
                latestByName.merge(instanceKey.getName(), instanceKey, (existingKey, newKey) -> {
                    int existingTag = Integer.parseInt(existingKey.getTag());
                    int newTag = Integer.parseInt(newKey.getTag());
                    return newTag > existingTag ? newKey : existingKey;
                });
            }

            // 6. For all the fetched manifests, if they are instances, associate the server with it, and send out a change
            for (Manifest.Key instance : latestByName.values()) {
                new ControllingMaster(centralHive, instance).associate(managedServerName);

                try {
                    // Additionally also read the last known instance overall state and return it...
                    InstanceManifest im = InstanceManifest.of(centralHive, instance);
                    result.states.add(new InstanceOverallStatusDto(InstanceManifest.getIdFromKey(instance),
                            im.getOverallState(centralHive).read()));
                } catch (Exception e) {
                    // This may be ignored
                    log.error("Cannot read instance overall state for {}: {}", instance, e.toString());
                    if (log.isDebugEnabled()) {
                        log.debug("Exception", e);
                    }
                }
            }

            // 7. Repeat for all systems which should also be associated with a server
            for (Manifest.Key system : managedSystems) {
                new ControllingMaster(centralHive, system).associate(managedServerName);
            }

            // 8. Try to sync instance group properties
            try {
                MasterSettingsResource managedMasterSettings = ResourceProvider.getVersionedResource(managedRemote,
                        MasterSettingsResource.class, context);
                managedMasterSettings.mergeInstanceGroupAttributesDescriptors(minion.getSettings().instanceGroup.attributes);
            } catch (Exception e) {
                log.warn("Cannot sync InstanceGroup properties to managed server, ignoring", e);
            }

            managedMasterDto.lastSync = Instant.now();
        }

        // 9. Fetch minion information and store in the managed masters
        Map<String, MinionStatusDto> status = managedBackendInfo.getNodeStatus();
        Map<String, MinionDto> config = status.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().config));
        managedMasterDto.minions = new MinionConfiguration(config);

        // 10. Check if managed server has newer products than central
        MinionProductUpdatesDto productUpdatesDto = new MinionProductUpdatesDto();
        productUpdatesDto.newerVersionAvailable = new HashMap<>();
        SortedSet<Key> productsOnCentral = ProductManifest.scan(centralHive);
        List<ProductDto> productsOnManaged = listProducts(groupName, managedServerName);
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

            productUpdatesDto.newerVersionAvailable.put(productName, allCentralVersionsAreOlder);
        }

        managedMasterDto.productUpdates = productUpdatesDto;

        // 11. Update current information in the hive
        managedMasters.attach(managedMasterDto, true);

        // 12. Send out notifications after *everything* is done
        for (var instanceKey : removedInstances) {
            changes.remove(ObjectChangeType.INSTANCE, instanceKey, new ObjectScope(groupName));
        }
        for (var systemKey : removedSystems) {
            changes.remove(ObjectChangeType.SYSTEM, systemKey, new ObjectScope(groupName));
        }

        changes.change(ObjectChangeType.INSTANCE_GROUP, centralIgm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));

        result.server = managedMasterDto;

        return result;
    }

    private static void syncInstanceGroup(RemoteService managedRemote, CommonRootResource managedCommonRoot, String groupName,
            BHive centralHive, InstanceGroupManifest centralIgm) {
        if (managedCommonRoot.getInstanceGroups().stream().noneMatch(igConfig -> groupName.equals(igConfig.name))) {
            throw new WebApplicationException("Instance group (no longer?) found on the managed server", Status.NOT_FOUND);
        }

        String attributesMetaName = centralIgm.getAttributes(centralHive).getMetaManifest().getMetaName();
        Set<Key> metaManifestKeys = centralHive.execute(new ManifestListOperation().setManifestName(attributesMetaName));

        PushOperation push = new PushOperation().setRemote(managedRemote).setHiveName(groupName).addManifest(centralIgm.getKey());
        metaManifestKeys.forEach(push::addManifest);
        centralHive.execute(push);
    }

    private void syncAddInstancesAndSystems(RemoteService managedRemote, String groupName, String managedServerName,
            BHive centralHive, List<String> managedInstanceIds, Collection<Key> managedSystems,
            Collection<String> managedSystemIds) {
        // Create the fetch operation
        FetchOperation fetchOp = new FetchOperation().setRemote(managedRemote).setHiveName(groupName);

        // Add all instance and meta manifests of the managed server to the fetch operation
        try (RemoteBHive rbh = RemoteBHive.forService(managedRemote, groupName, new ActivityReporter.Null())) {
            // Add all instance manifests to the fetch operation
            rbh.getManifestInventory(managedInstanceIds.toArray(String[]::new)).keySet().forEach(fetchOp::addManifest);

            // Add all the related meta manifests to the fetch operation
            rbh.getManifestInventory(managedInstanceIds.stream().map(s -> MetaManifest.META_PREFIX + s).toArray(String[]::new))
                    .keySet().forEach(fetchOp::addManifest);
        }

        // Add all systems of the managed server to the fetch operation
        try {
            ResourceProvider.getVersionedResource(managedRemote, MasterRootResource.class, context).getNamedMaster(groupName)
                    .getSystemResource().list().forEach((systemKey, systemConfig) -> {
                        managedSystems.add(systemKey);
                        managedSystemIds.add(systemConfig.id);
                    });
            fetchOp.addManifest(managedSystems);
        } catch (Exception e) {
            log.info("Cannot fetch systems from {}: {}", managedServerName, e.toString());
            if (log.isDebugEnabled()) {
                log.debug("Exception", e);
            }
        }

        // Execute the fetch operation
        centralHive.execute(fetchOp);
    }

    private static void syncRemoveInstancesAndSystems(String managedServerName, BHive centralHive,
            Collection<Key> managedInstances, Collection<String> managedSystemIds, Collection<Key> removedInstances,
            Collection<Key> removedSystems) {
        // Remove local instances no longer available on the remote
        SortedSet<Key> instancesOnCentral = InstanceManifest.scan(centralHive, false);
        for (Key instanceKey : instancesOnCentral) {
            if (managedInstances.contains(instanceKey)) {
                continue; // OK. instance exists.
            }
            if (!managedServerName.equals(new ControllingMaster(centralHive, instanceKey).read().getName())) {
                continue; // OK. other server.
            }

            // Not OK: instance no longer on server. We need to make sure to also delete the node manifests of that instance version!
            InstanceManifest.delete(centralHive, instanceKey);
            removedInstances.add(instanceKey);
        }

        // Remove local systems no longer available on the remote
        SortedSet<Key> systemsOnCentral = SystemManifest.scan(centralHive);
        for (Key systemKey : systemsOnCentral) {
            SystemManifest sm = SystemManifest.of(centralHive, systemKey);
            if (managedSystemIds.contains(sm.getConfiguration().id)) {
                continue; // OK. system exists.
            }
            if (!managedServerName.equals(new ControllingMaster(centralHive, systemKey).read().getName())) {
                continue; // OK. other server.
            }

            // Not OK: system no longer on server
            Set<Key> allSystemMfs = centralHive.execute(new ManifestListOperation().setManifestName(sm.getKey().getName())); // all versions
            allSystemMfs.forEach(key -> centralHive.execute(new ManifestDeleteOperation().setToDelete(key)));
            removedSystems.add(systemKey);
        }
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
            inventory.keySet().stream().map(ScopedManifestKey::parse).filter(Objects::nonNull)
                    .forEach(scopedKey -> remoteVersions.add(scopedKey));
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
            BHive hive = getInstanceGroupHive(groupName);

            ManagedMasters mm = new ManagedMasters(hive);
            ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
            Map<String, MinionDto> allMinions = attached.minions.minionMap();

            // Determine OS of the master
            Optional<MinionDto> masterDto = allMinions.values().stream().filter(dto -> dto.master).findFirst();
            if (!masterDto.isPresent()) {
                throw new WebApplicationException("Cannot determine master node");
            }

            // Trigger the update on the master node (only retain server packages in the list of packages to install)
            RemoteService svc = getConfiguredRemote(groupName, serverName);
            Collection<Key> server = updates.packagesToInstall.stream().filter(UpdateHelper::isBDeployServerKey).toList();
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
        return result.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull)
                .filter(smk -> smk.getTag().equals(runningVersion)).collect(Collectors.toSet());
    }

}
