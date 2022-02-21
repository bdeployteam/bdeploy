package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.Map;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Management resource which more or less plain-redirects to the local MasterRootResource.
 * <p>
 * In case of CENTRAL does nothing. A dedicated implementation is required for CENTRAL.
 */
public class NodeManagementResourceImpl implements NodeManagementResource {

    @Inject
    private Minion minion;

    @Context
    private SecurityContext context;

    @Override
    public Map<String, MinionStatusDto> getNodes() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return Collections.emptyMap();
        }
        return ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).getNodes();
    }

    @Override
    public void addNode(String name, RemoteService node) {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return;
        }
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).addNode(name, node);
    }

    @Override
    public void removeNode(String name) {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return;
        }
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).removeNode(name);
    }

}
