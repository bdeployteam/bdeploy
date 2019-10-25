package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.LocalMasterAssociationMetaManifest;
import io.bdeploy.ui.LocalMasterAttachmentsMetaManifest;
import io.bdeploy.ui.api.LocalServersResource;
import io.bdeploy.ui.dto.AttachIdentDto;

public class LocalServersResourceImpl implements LocalServersResource {

    private static final Logger log = LoggerFactory.getLogger(LocalServersResourceImpl.class);

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

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

        BHive hive = registry.get(groupName);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + groupName + " locally", Status.NOT_FOUND);
        }

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
        BHive hive = registry.get(groupName);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + groupName + " locally", Status.NOT_FOUND);
        }

        if (LocalMasterAttachmentsMetaManifest.read(hive).getAttachedLocalServers().containsKey(target.name)) {
            throw new WebApplicationException("Server with name " + target.name + " already exists!", Status.CONFLICT);
        }

        // store the attachment locally
        LocalMasterAttachmentsMetaManifest.attach(hive, target, false);
    }

    @Override
    public List<String> getServerNames(String instanceGroup) {
        BHive hive = registry.get(instanceGroup);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + instanceGroup + " locally");
        }

        LocalMasterAttachmentsMetaManifest masters = LocalMasterAttachmentsMetaManifest.read(hive);
        return new ArrayList<>(masters.getAttachedLocalServers().keySet());
    }

    @Override
    public AttachIdentDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = registry.get(instanceGroup);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + instanceGroup + " locally");
        }

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

}
