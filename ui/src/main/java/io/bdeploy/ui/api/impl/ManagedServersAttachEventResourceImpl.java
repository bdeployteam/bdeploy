package io.bdeploy.ui.api.impl;

import java.util.Map;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class ManagedServersAttachEventResourceImpl implements ManagedServersAttachEventResource {

    @Inject
    private ChangeEventManager changes;

    @Inject
    private BHiveRegistry reg;

    @Override
    @POST
    public void setLocalAttached(String groupName) {
        changes.create(ObjectChangeType.MANAGED_MASTER_ATTACH, Map.of(ObjectChangeDetails.CHANGE_HINT, groupName));

        BHive hive = reg.get(groupName);
        if (hive == null) {
            throw new WebApplicationException("Instance Group not found", Status.NOT_FOUND);
        }

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.create(ObjectChangeType.INSTANCE_GROUP, igm.getKey());
    }

}
