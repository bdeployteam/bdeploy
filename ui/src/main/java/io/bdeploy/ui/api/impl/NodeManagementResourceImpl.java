package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.nodes.NodeListDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.CreateMultiNodeDto;
import io.bdeploy.ui.dto.NodeAttachDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Management resource which more or less plain-redirects to the local {@link MasterRootResource}.
 * <p>
 * Most calls throw if the current {@link Minion} has {@link MinionMode#CENTRAL}. A dedicated implementation is required for this
 * case.
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
        return getNodeList().nodes;
    }

    @Override
    public NodeListDto getNodeList() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return new NodeListDto();
        }
        var mrr = ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context);
        var result = new NodeListDto();
        result.nodes = mrr.getNodes();
        result.multiNodeToRuntimeNodes = mrr.getMultiNodeToRuntimeNodes();
        return result;
    }

    @Override
    public void addServerNode(NodeAttachDto data) {
        throwIfCentral();
        if (data.sourceMode == MinionMode.NODE) {
            ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context)
                    .addServerNode(data.name, data.remote);
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

    @Override
    public void addMultiNode(CreateMultiNodeDto createMultiNodeDto) {
        throwIfCentral();
        if (createMultiNodeDto.config.operatingSystem == null || OsHelper.isNotSupported(createMultiNodeDto.config.operatingSystem)) {
            throw new WebApplicationException("To add a multi-node, the operating system must be entered and must be supported.");
        }
        ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context)
                .addMultiNode(createMultiNodeDto.name, createMultiNodeDto.config);
    }

    @Override
    public MultiNodeMasterFile getMultiNodeMasterFile(String name) {
        throwIfCentral();

        MultiNodeMasterFile desc = new MultiNodeMasterFile();
        desc.name = name;
        desc.master = new RemoteService(minion.getSelf().getUri(),
                ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).generateWeakToken(name));

        return desc;
    }
}
