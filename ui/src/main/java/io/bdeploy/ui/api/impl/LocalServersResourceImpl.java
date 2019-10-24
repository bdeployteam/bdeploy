package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
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
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.LocalMasterAssociationMetaManifest;
import io.bdeploy.ui.LocalMasterAttachmentsMetaManifest;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.LocalServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.AttachIdentDto;
import io.bdeploy.ui.dto.CentralIdentDto;

public class LocalServersResourceImpl implements LocalServersResource {

    private static final Logger log = LoggerFactory.getLogger(LocalServersResourceImpl.class);

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Override
    public void tryAutoAttach(String groupName, AttachIdentDto target) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(target.uri).build(), target.auth);
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        for (InstanceGroupConfiguration cfg : root.getInstanceGroups()) {
            if (cfg.name.equals(groupName)) {
                throw new WebApplicationException("Instance Group with name " + groupName + " already exists on the local server",
                        Status.CONFLICT);
            }
        }

        BHive hive = getInstanceGroupHive(groupName);

        if (LocalMasterAttachmentsMetaManifest.read(hive).getAttachedLocalServers().containsKey(target.name)) {
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
            LocalMasterAttachmentsMetaManifest.attach(hive, target, false);

            WebTarget attachTarget = ResourceProvider.of(svc).getBaseTarget().path("/attach-events");
            StatusType status = attachTarget.request().buildPost(Entity.text(groupName)).invoke().getStatusInfo();
            if (status.getFamily() != Family.SUCCESSFUL) {
                throw new IllegalStateException("Cannot notify server of successful attachment: " + status);
            }
        } catch (Exception e) {
            log.error("Auto-attach failed", e);
            throw new WebApplicationException("Cannot automatically attach local server " + target.name, e);
        }
    }

    @Override
    public void manualAttach(String groupName, AttachIdentDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        if (LocalMasterAttachmentsMetaManifest.read(hive).getAttachedLocalServers().containsKey(target.name)) {
            throw new WebApplicationException("Server with name " + target.name + " already exists!", Status.CONFLICT);
        }

        // store the attachment locally
        LocalMasterAttachmentsMetaManifest.attach(hive, target, false);
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
            try (ByteArrayInputStream bis = new ByteArrayInputStream(dto.logo)) {
                MultiPart mp = new MultiPart();
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

        if (LocalMasterAttachmentsMetaManifest.read(hive).getAttachedLocalServers().containsKey(target.name)) {
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
    public List<AttachIdentDto> getLocalServers(String instanceGroup) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        LocalMasterAttachmentsMetaManifest masters = LocalMasterAttachmentsMetaManifest.read(hive);
        return masters.getAttachedLocalServers().values().stream().map(e -> {
            e.auth = null;
            return e;
        }).collect(Collectors.toList());
    }

    @Override
    public AttachIdentDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        LocalMasterAttachmentsMetaManifest masters = LocalMasterAttachmentsMetaManifest.read(hive);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, instanceTag);

        String selected = LocalMasterAssociationMetaManifest.read(hive, im.getManifest());
        if (selected == null) {
            return null;
        }

        AttachIdentDto dto = masters.getAttachedLocalServers().get(selected);
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
            String associated = LocalMasterAssociationMetaManifest.read(hive, key);
            if (serverName.equals(associated)) {
                instances.add(InstanceManifest.of(hive, key).getConfiguration());
            }
        }
        return instances;
    }

    @Override
    public void deleteLocalServer(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);
        List<InstanceConfiguration> controlled = getInstancesControlledBy(groupName, serverName);

        // delete all of the instances /LOCALLY/ on the central, but NOT using the remote master (we "just" detach).
        for (InstanceConfiguration cfg : controlled) {
            SortedSet<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(cfg.uuid));
            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
        }

        LocalMasterAttachmentsMetaManifest.detach(hive, serverName);
    }

    @Override
    public Map<String, NodeStatus> getMinionsOfLocalServer(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        AttachIdentDto attached = LocalMasterAttachmentsMetaManifest.read(hive).getAttachedLocalServers().get(serverName);
        if (attached == null) {
            throw new WebApplicationException("Local server " + serverName + " not found for instance group " + groupName,
                    Status.NOT_FOUND);
        }

        RemoteService svc = new RemoteService(UriBuilder.fromUri(attached.uri).build(), attached.auth);
        return ResourceProvider.getResource(svc, MasterRootResource.class, context).getMinions();
    }

}
