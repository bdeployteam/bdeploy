package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.Map;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.dto.BackendInfoDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

public class BackendInfoResourceImpl implements BackendInfoResource {

    @Inject
    private Minion minion;

    @Inject
    private NodeManager nodes;

    @Context
    private UriInfo info;

    @Override
    public BackendInfoDto getVersion() {
        return new BackendInfoDto(VersionHelper.getVersion(), minion.getMode(), minion.getHostName());
    }

    @Override
    public ManagedMasterDto getManagedMasterIdentification() {
        ManagedMasterDto dto = new ManagedMasterDto();

        dto.hostName = minion.getHostName();
        dto.auth = nodes.getSelf().remote.getAuthPack();
        dto.uri = info.getBaseUri().toString();
        dto.minions = new MinionConfiguration(nodes.getAllNodes());

        return dto;
    }

    @Override
    public Map<String, MinionStatusDto> getNodeStatus() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            return Collections.singletonMap(nodes.getSelfName(),
                    ResourceProvider.getResource(nodes.getSelf().remote, MinionStatusResource.class, null).getStatus());
        }
        return ResourceProvider.getVersionedResource(nodes.getSelf().remote, MasterRootResource.class, null).getNodes();
    }

}
