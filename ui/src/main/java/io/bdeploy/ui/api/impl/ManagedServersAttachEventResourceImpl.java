package io.bdeploy.ui.api.impl;

import java.util.Map;

import io.bdeploy.ui.api.ManagedServersAttachEventResource;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;

public class ManagedServersAttachEventResourceImpl implements ManagedServersAttachEventResource {

    @Inject
    private ChangeEventManager changes;

    @Override
    @POST
    public void setLocalAttached(String groupName) {
        changes.create(ObjectChangeType.MANAGED_MASTER_ATTACH, Map.of(ObjectChangeDetails.CHANGE_HINT, groupName));
    }

}
