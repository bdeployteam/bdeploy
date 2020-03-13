package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.CentralIdentDto;
import io.bdeploy.ui.dto.MinionUpdateDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;

public class ManagedServersResourceImpl implements ManagedServersResource {

    private static final Logger log = LoggerFactory.getLogger(ManagedServersResourceImpl.class);

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private ProductTransferService transfers;

    @Override
    public void tryAutoAttach(String groupName, ManagedMasterDto target) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(target.uri).build(), target.auth);
        CommonRootResource root = ResourceProvider.getResource(svc, CommonRootResource.class, context);

        boolean igExists = false;
        for (InstanceGroupConfiguration cfg : root.getInstanceGroups()) {
            if (cfg.name.equals(groupName)) {
                igExists = true; // don't try to create, instead sync
            }
        }

        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.CONFLICT);
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

            ResourceProvider.getResource(svc, ManagedServersAttachEventResource.class, context).setLocalAttached(groupName);
        } catch (Exception e) {
            throw new WebApplicationException("Cannot automatically attach managed server " + target.hostName, e);
        }
    }

    @Override
    public void manualAttach(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.CONFLICT);
        }

        // store the attachment locally
        mm.attach(target, false);
    }

    @Override
    public String manualAttachCentral(String central) {
        CentralIdentDto dto = minion.getDecryptedPayload(central, CentralIdentDto.class);

        RemoteService selfSvc = minion.getSelf();
        RemoteService testSelf = new RemoteService(selfSvc.getUri(), dto.local.auth);

        // will throw if there is an authentication problem.
        InstanceGroupResource self = ResourceProvider.getResource(testSelf, InstanceGroupResource.class, context);

        dto.config.logo = null; // later.
        self.create(dto.config);
        if (dto.logo != null) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(dto.logo); MultiPart mp = new MultiPart();) {
                StreamDataBodyPart bp = new StreamDataBodyPart("image", bis);
                bp.setFilename("logo.png");
                bp.setMediaType(new MediaType("image", "png"));
                mp.bodyPart(bp);

                WebTarget target = ResourceProvider.of(testSelf).getBaseTarget().path("/group/" + dto.config.name + "/image");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new IllegalStateException("Image update failed: " + response.getStatusInfo().getReasonPhrase());
                }
            } catch (IOException e) {
                log.error("Failed to update instance group logo", e);
            }
        }
        return dto.config.name;
    }

    @Override
    public String getCentralIdent(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        if (new ManagedMasters(hive).read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.CONFLICT);
        }

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
        }).collect(Collectors.toList());
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
            throw new WebApplicationException(
                    "Recorded managed server for instance " + instanceId + " no longer available on instance group: " + selected,
                    Status.NOT_FOUND);
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
        BHive hive = getInstanceGroupHive(groupName);
        List<InstanceConfiguration> controlled = getInstancesControlledBy(groupName, serverName);

        // delete all of the instances /LOCALLY/ on the central, but NOT using the remote master (we "just" detach).
        for (InstanceConfiguration cfg : controlled) {
            SortedSet<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(cfg.uuid));
            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
        }

        new ManagedMasters(hive).detach(serverName);
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
    }

    @Override
    public Map<String, MinionDto> getMinionsOfManagedServer(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        return attached.minions.values();
    }

    @Override
    public Map<String, MinionStatusDto> getMinionStateOfManagedServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        return root.getMinions();
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
    public ManagedMasterDto synchronize(String groupName, String serverName) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return null;
        }
        BHive hive = getInstanceGroupHive(groupName);
        RemoteService svc = getConfiguredRemote(groupName, serverName);

        BackendInfoResource backendInfo = ResourceProvider.getResource(svc, BackendInfoResource.class, context);
        if (backendInfo.getVersion().mode != MinionMode.MANAGED) {
            throw new WebApplicationException("Server is no longer in managed mode: " + serverName, Status.EXPECTATION_FAILED);
        }

        // 1. Sync instance group data with managed server.
        CommonRootResource root = ResourceProvider.getResource(svc, CommonRootResource.class, context);
        if (root.getInstanceGroups().stream().map(g -> g.name).noneMatch(n -> n.equals(groupName))) {
            throw new WebApplicationException("Instance group (no longer?) found on the managed server", Status.NOT_FOUND);
        }

        Manifest.Key igKey = new InstanceGroupManifest(hive).getKey();
        try (RemoteBHive rbh = RemoteBHive.forService(svc, groupName, reporter)) {
            // ALWAYS delete all instance group information on the target - we win!
            // otherwise the target may have a manifest with a higher tag number and win.
            SortedMap<Key, ObjectId> keys = rbh.getManifestInventory(igKey.getName());
            // maybe not optimal to do a call per manifest...
            keys.keySet().forEach(rbh::removeManifest);
        }
        hive.execute(new PushOperation().addManifest(igKey).setRemote(svc).setHiveName(groupName));

        // 2. Fetch all instance and meta manifests, no products.
        CommonRootResource masterRoot = ResourceProvider.getResource(svc, CommonRootResource.class, context);
        CommonInstanceResource master = masterRoot.getInstanceResource(groupName);
        SortedMap<Key, InstanceConfiguration> instances = master.listInstanceConfigurations(true);
        List<String> instanceIds = instances.values().stream().map(ic -> ic.uuid).collect(Collectors.toList());

        FetchOperation fetchOp = new FetchOperation().setRemote(svc).setHiveName(groupName);
        try (RemoteBHive rbh = RemoteBHive.forService(svc, groupName, reporter)) {
            SortedSet<Manifest.Key> keysToFetch = new TreeSet<>();

            // maybe we can scope this down a little in the future.
            rbh.getManifestInventory(instanceIds.toArray(String[]::new)).forEach((k, v) -> keysToFetch.add(k));

            // we're also interested in all the related meta manifests.
            rbh.getManifestInventory(instanceIds.stream().map(s -> MetaManifest.META_PREFIX + s).toArray(String[]::new))
                    .forEach((k, v) -> keysToFetch.add(k));

            // set calculated keys to fetch operation.
            keysToFetch.forEach(fetchOp::addManifest);
        }

        hive.execute(fetchOp);

        // 3. Remove local instances no longer available on the remote
        SortedSet<Key> keysOnCentral = InstanceManifest.scan(hive, true);
        for (Key key : keysOnCentral) {
            InstanceManifest im = InstanceManifest.of(hive, key);
            if (instanceIds.contains(im.getConfiguration().uuid)) {
                continue; // OK. instance exists
            }

            if (!serverName.equals(new ControllingMaster(hive, key).read().getName())) {
                continue; // OK. other server or null (should not happen).
            }

            // Not OK: instance no longer on server.
            SortedSet<Key> allInstanceObjects = hive
                    .execute(new ManifestListOperation().setManifestName(im.getConfiguration().uuid));
            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
        }

        // 4. for all the fetched manifests, if they are instances, associate the server with it
        for (Manifest.Key instance : instances.keySet()) {
            new ControllingMaster(hive, instance).associate(serverName);
        }

        // 5. Fetch minion information and store in the managed masters
        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        Map<String, MinionStatusDto> status = backendInfo.getNodeStatus();
        Map<String, MinionDto> config = status.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().config));
        MinionConfiguration minions = attached.minions;
        minions.replaceWith(config);

        // 5. update last sync timestamp on attachment of server.
        attached.lastSync = Instant.now();
        mm.attach(attached, true);
        return attached;
    }

    @Override
    public List<ProductDto> listProducts(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        return ResourceProvider.getResource(svc, InstanceGroupResource.class, context).getProductResource(groupName).list();
    }

    @Override
    public void transferProducts(String groupName, ProductTransferDto transfer) {
        transfers.initTransfer(getInstanceGroupHive(groupName), groupName, transfer);
    }

    @Override
    public SortedSet<ProductDto> getActiveTransfers(String groupName) {
        return transfers.getActiveTransfers(groupName);
    }

    @Override
    public MinionUpdateDto getUpdates(String groupName, String serverName) {
        // Determine OS of the master
        Optional<MinionDto> masterDto = getMinionsOfManagedServer(groupName, serverName).values().stream()
                .filter(dto -> dto.master).findFirst();
        if (!masterDto.isPresent()) {
            throw new WebApplicationException("Cannot determine master node");
        }

        Version runningVersion = VersionHelper.getVersion();
        Version managedVersion = masterDto.get().version;

        // Determine whether or not an update must be installed
        MinionUpdateDto updateDto = new MinionUpdateDto();
        updateDto.updateVersion = runningVersion;
        updateDto.runningVersion = managedVersion;
        updateDto.updateAvailable = VersionHelper.compare(runningVersion, managedVersion) > 0;
        updateDto.forceUpdate = runningVersion.getMajor() > managedVersion.getMajor();

        // Contact the remote service to find out all installed versions
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        Set<ScopedManifestKey> remoteVersions = new HashSet<>();
        try (RemoteBHive rbh = RemoteBHive.forService(svc, null, reporter)) {
            SortedMap<Key, ObjectId> inventory = rbh.getManifestInventory(SoftwareUpdateResource.BDEPLOY_MF_NAME,
                    SoftwareUpdateResource.LAUNCHER_MF_NAME);
            inventory.keySet().stream().forEach(key -> remoteVersions.add(ScopedManifestKey.parse(key)));
        }

        // Determine what is available in our hive
        Set<ScopedManifestKey> localVersion = new HashSet<>();
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.BDEPLOY_MF_NAME));
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.LAUNCHER_MF_NAME));

        // Compute what is missing and what needs to be installed
        updateDto.packagesToInstall = localVersion.stream().map(ScopedManifestKey::getKey).collect(Collectors.toList());
        localVersion.removeAll(remoteVersions);
        updateDto.packagesToTransfer = localVersion.stream().map(ScopedManifestKey::getKey).collect(Collectors.toList());

        return updateDto;
    }

    @Override
    public void transferUpdate(String groupName, String serverName, MinionUpdateDto updates) {
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
    }

    @Override
    public void installUpdate(String groupName, String serverName, MinionUpdateDto updates) {
        // Only retain server packages in the list of packages to install
        Collection<Key> keys = updates.packagesToInstall;
        Collection<Key> server = keys.stream().filter(UpdateHelper::isBDeployServerKey).collect(Collectors.toList());

        // Determine OS of the master
        Optional<MinionDto> masterDto = getMinionsOfManagedServer(groupName, serverName).values().stream()
                .filter(dto -> dto.master).findFirst();
        if (!masterDto.isPresent()) {
            throw new WebApplicationException("Cannot determine master node");
        }

        // Trigger the update on the master node
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        UpdateHelper.update(svc, server, true);
    }

    @Override
    public Version pingServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        BackendInfoResource info = ResourceProvider.getResource(svc, BackendInfoResource.class, null);
        return info.getVersion().version;
    }

    private Collection<ScopedManifestKey> getLocalPackage(String manifestName) {
        String runningVersion = VersionHelper.getVersion().toString();

        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        ManifestListOperation operation = new ManifestListOperation().setManifestName(manifestName);
        SortedSet<Key> result = defaultHive.execute(operation);
        return result.stream().map(ScopedManifestKey::parse).filter(smk -> smk.getTag().equals(runningVersion))
                .collect(Collectors.toSet());
    }

    @Override
    public Boolean isDataMigrationRequired(String groupName) {
        BHive hive = getInstanceGroupHive(groupName);

        SortedSet<Key> instances = InstanceManifest.scan(hive, false);
        for (Key key : instances) {
            InstanceManifest im = InstanceManifest.of(hive, key);

            for (Key nodeKey : im.getInstanceNodeManifests().values()) {
                if (hive.execute(new ManifestRefScanOperation().setManifest(nodeKey)).size() > 0) {
                    // it has a manifest reference, which is not allowed... this is the hint that this
                    // node still uses the old scheme which referenced applications. this will destroy
                    // our sync as it would sync applications instead of config only.
                    return Boolean.TRUE;
                }
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public void performDataMigration(String groupName) {
        BHive hive = getInstanceGroupHive(groupName);

        MasterNamedResource master = ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context)
                .getNamedMaster(groupName);

        SortedSet<Key> scan = InstanceManifest.scan(hive, true);

        for (Key k : scan) {
            log.info("Migrating " + k.getName() + ", using tag " + k.getTag());
            InstanceManifest im = InstanceManifest.of(hive, k);

            // dummy update, re-use existing configuration. new manifests will be written without references
            InstanceUpdateDto update = new InstanceUpdateDto(new InstanceConfigurationDto(im.getConfiguration(), null), null);
            Key newKey = master.update(update, k.getTag());

            // now remove all previous versions of the instance (and it's nodes by matching segments of the manifest name.)
            SortedSet<Key> keys = hive.execute(new ManifestListOperation().setManifestName(im.getConfiguration().uuid));

            for (Key any : keys) {
                if (any.getTag().equals(newKey.getTag())) {
                    continue;
                }
                hive.execute(new ManifestDeleteOperation().setToDelete(any));
            }
        }
    }

}
