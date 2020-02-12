package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.BackendInfoDto;

public class BackendInfoResourceImpl implements BackendInfoResource {

    @Inject
    private Minion minion;

    @Context
    private UriInfo info;

    @Override
    public BackendInfoDto getVersion() {
        return new BackendInfoDto(VersionHelper.getVersion(), minion.getMode());
    }

    @Override
    public ManagedMasterDto getManagedMasterIdentification() {
        ManagedMasterDto dto = new ManagedMasterDto();

        dto.hostName = minion.getHostName();
        dto.auth = minion.getSelf().getAuthPack();
        dto.uri = info.getBaseUri().toString();
        dto.minions = minion.getMinions();

        return dto;
    }

    @Override
    public Map<String, MinionStatusDto> getNodeStatus() {
        RemoteService remote = minion.getSelf();

        // Central server does not have any nodes. Thus return self only
        if (minion.getMode() == MinionMode.CENTRAL) {
            String name = minion.getMinions().values().keySet().iterator().next();
            MinionStatusResource status = ResourceProvider.getResource(remote, MinionStatusResource.class, null);
            return Collections.singletonMap(name, status.getStatus());
        }

        // Delegate to the master to find out all nodes and their state
        MasterRootResource root = ResourceProvider.getResource(remote, MasterRootResource.class, null);
        return root.getMinions();
    }

}
