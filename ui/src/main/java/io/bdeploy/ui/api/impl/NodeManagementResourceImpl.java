package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.NodeAttachDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
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

    @Context
    private ResourceContext rc;

    @Override
    public Map<String, MinionStatusDto> getNodes() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return Collections.emptyMap();
        }
        return ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).getNodes();
    }

    @Override
    public void addNode(NodeAttachDto data) {
        throwIfCentral();
        if (data.sourceMode == MinionMode.NODE) {
            ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).addNode(data.name, data.remote);
        } else {
            ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).convertNode(data.name, data.remote);
        }
    }

    @Override
    public void editNode(String name, RemoteService node) {
        throwIfCentral();
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).editNode(name, node);
    }

    @Override
    public void removeNode(String name) {
        throwIfCentral();
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).removeNode(name);
    }

    @Override
    public void updateNode(String name, List<Key> keys) {
        throwIfCentral();

        MasterRootResource root = ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context);

        // We need to sort the updates so that the running OS is the last one
        keys.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull).sorted((a, b) -> {
            // put own OS last.
            if (a.getOperatingSystem() != b.getOperatingSystem()) {
                return a.getOperatingSystem() == OsHelper.getRunningOs() ? 1 : -1;
            }
            return a.getKey().toString().compareTo(b.getKey().toString());
        }).forEach(k -> root.updateNode(name, k.getKey(), true));
    }

    @Override
    public void replaceNode(String name, RemoteService node) {
        throwIfCentral();
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).replaceNode(name, node);
    }

    @Override
    public RepairAndPruneResultDto repairAndPruneNode(String name) {
        RepairAndPruneResultDto result = new RepairAndPruneResultDto();
        result.repaired = ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).fsckNode(name);
        result.pruned = FormatHelper.formatFileSize(
                ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).pruneNode(name));
        return result;
    }

    @Override
    public void restartNode(String name) {
        throwIfCentral();
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).restartNode(name);
    }

    @Override
    public void shutdownNode(String name) {
        throwIfCentral();
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).shutdownNode(name);
    }

    private void throwIfCentral() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            throw new WebApplicationException("Operation not available in mode CENTRAL");
        }
    }
}
