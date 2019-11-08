package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.ManagedMasterAssociationMetaManifest;
import io.bdeploy.ui.ManagedMasterAttachmentsMetaManifest;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.AttachIdentDto;
import io.bdeploy.ui.dto.CentralIdentDto;

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

    @Override
    public void tryAutoAttach(String groupName, AttachIdentDto target) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(target.uri).build(), target.auth);
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        for (InstanceGroupConfiguration cfg : root.getInstanceGroups()) {
            if (cfg.name.equals(groupName)) {
                throw new WebApplicationException(
                        "Instance Group with name " + groupName + " already exists on the managed server", Status.CONFLICT);
            }
        }

        BHive hive = getInstanceGroupHive(groupName);

        if (ManagedMasterAttachmentsMetaManifest.read(hive).getAttachedManagedServers().containsKey(target.name)) {
            throw new WebApplicationException("Server with name " + target.name + " already exists!", Status.CONFLICT);
        }

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        InstanceGroupConfiguration igc = igm.read();
        igc.logo = null;

        try {
            // initial create without logo - required to create instance group hive.
            root.addInstanceGroup(igc, null);

            // push the latest instance group manifest to the target
            hive.execute(new PushOperation().setHiveName(groupName).addManifest(igm.getKey()).setRemote(svc));

            // store the attachment locally
            ManagedMasterAttachmentsMetaManifest.attach(hive, target, false);

            WebTarget attachTarget = ResourceProvider.of(svc).getBaseTarget().path("/attach-events");
            StatusType status = attachTarget.request().buildPost(Entity.text(groupName)).invoke().getStatusInfo();
            if (status.getFamily() != Family.SUCCESSFUL) {
                throw new IllegalStateException("Cannot notify server of successful attachment: " + status);
            }
        } catch (Exception e) {
            throw new WebApplicationException("Cannot automatically attach managed server " + target.name, e);
        }
    }

    @Override
    public void manualAttach(String groupName, AttachIdentDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        if (ManagedMasterAttachmentsMetaManifest.read(hive).getAttachedManagedServers().containsKey(target.name)) {
            throw new WebApplicationException("Server with name " + target.name + " already exists!", Status.CONFLICT);
        }

        // store the attachment locally
        ManagedMasterAttachmentsMetaManifest.attach(hive, target, false);
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
    public String getCentralIdent(String groupName, AttachIdentDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        if (ManagedMasterAttachmentsMetaManifest.read(hive).getAttachedManagedServers().containsKey(target.name)) {
            throw new WebApplicationException("Server with name " + target.name + " already exists!", Status.CONFLICT);
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
    public List<AttachIdentDto> getManagedServers(String instanceGroup) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMasterAttachmentsMetaManifest masters = ManagedMasterAttachmentsMetaManifest.read(hive);
        return masters.getAttachedManagedServers().values().stream().map(e -> {
            e.auth = null;
            return e;
        }).collect(Collectors.toList());
    }

    @Override
    public AttachIdentDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMasterAttachmentsMetaManifest masters = ManagedMasterAttachmentsMetaManifest.read(hive);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, instanceTag);

        String selected = ManagedMasterAssociationMetaManifest.read(hive, im.getManifest()).getMasterName();
        if (selected == null) {
            return null;
        }

        AttachIdentDto dto = masters.getAttachedManagedServers().get(selected);
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
            String associated = ManagedMasterAssociationMetaManifest.read(hive, key).getMasterName();
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

        ManagedMasterAttachmentsMetaManifest.detach(hive, serverName);
    }

    @Override
    public Map<String, NodeStatus> getMinionsOfManagedServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        return ResourceProvider.getResource(svc, MasterRootResource.class, context).getMinions();
    }

    private RemoteService getConfiguredRemote(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        AttachIdentDto attached = ManagedMasterAttachmentsMetaManifest.read(hive).getAttachedManagedServers().get(serverName);
        if (attached == null) {
            throw new WebApplicationException("Managed server " + serverName + " not found for instance group " + groupName,
                    Status.NOT_FOUND);
        }

        RemoteService svc = new RemoteService(UriBuilder.fromUri(attached.uri).build(), attached.auth);
        return svc;
    }

    @Override
    public void synchronize(String groupName, String serverName) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        BHive hive = getInstanceGroupHive(groupName);
        RemoteService svc = getConfiguredRemote(groupName, serverName);

        // 1. Sync instance group data with managed server.
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        if (!root.getInstanceGroups().stream().map(g -> g.name).filter(n -> n.equals(groupName)).findAny().isPresent()) {
            throw new WebApplicationException("Instance group (no longer?) found on the managed server", Status.NOT_FOUND);
        }

        Manifest.Key igKey = new InstanceGroupManifest(hive).getKey();
        hive.execute(new PushOperation().addManifest(igKey).setRemote(svc).setHiveName(groupName));

        // 2. Fetch all instance and meta manifests, no products.
        MasterNamedResource master = root.getNamedMaster(groupName);
        SortedMap<Key, InstanceConfiguration> instances = master.listInstanceConfigurations(true);
        List<String> instanceIds = instances.values().stream().map(ic -> ic.uuid).collect(Collectors.toList());

        FetchOperation fetchOp = new FetchOperation().setRemote(svc).setHiveName(groupName);

        try (RemoteBHive rbh = RemoteBHive.forService(svc, groupName, reporter)) {
            SortedSet<Manifest.Key> keysToFetch = new TreeSet<>();

            // maybe we can scope this down a little in the future.
            rbh.getManifestInventory(instanceIds.toArray(String[]::new)).forEach((k, v) -> keysToFetch.add(k));

            // we're also interested in all the related meta manifests.
            rbh.getManifestInventory(instanceIds.stream().map(s -> ".meta/" + s).toArray(String[]::new))
                    .forEach((k, v) -> keysToFetch.add(k));

            // .. and also in the minion information. (FIXME)
        }

        hive.execute(fetchOp);

        // 3. Remove local instances no longer available on the remote
        SortedSet<Key> keysOnCentral = InstanceManifest.scan(hive, true);
        for (Key key : keysOnCentral) {
            InstanceManifest im = InstanceManifest.of(hive, key);
            if (instanceIds.contains(im.getConfiguration().uuid)) {
                continue; // OK. instance exists
            }

            if (!serverName.equals(ManagedMasterAssociationMetaManifest.read(hive, key).getMasterName())) {
                continue; // OK. other server
            }

            // Not OK: instance no longer on server.
            SortedSet<Key> allInstanceObjects = hive
                    .execute(new ManifestListOperation().setManifestName(im.getConfiguration().uuid));
            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
        }

        // 4. for all the fetched manifests, if they are instances, associate the server with it
        for (Manifest.Key instance : instances.keySet()) {
            ManagedMasterAssociationMetaManifest.associate(hive, instance, serverName);
        }

        // 5. update last sync timestamp on attachment of server.
        AttachIdentDto attached = ManagedMasterAttachmentsMetaManifest.read(hive).getAttachedManagedServers().get(serverName);
        attached.lastSync = Instant.now();
        ManagedMasterAttachmentsMetaManifest.attach(hive, attached, true);
    }

}
