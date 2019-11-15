package io.bdeploy.ui.api.impl;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.BackendInfoDto;
import io.bdeploy.ui.dto.ManagedMasterDto;

public class BackendInfoResourceImpl implements BackendInfoResource {

    @Inject
    private Minion minion;

    @Context
    private UriInfo info;

    @Override
    public BackendInfoDto getVersion() {
        return new BackendInfoDto(VersionHelper.readVersion(), minion.getMode());
    }

    @Override
    public ManagedMasterDto getManagedMasterIdentification() {
        ManagedMasterDto dto = new ManagedMasterDto();

        dto.name = minion.getOfficialName();
        dto.auth = minion.getSelf().getAuthPack();
        dto.uri = info.getBaseUri().toString();
        dto.minions = minion.getMinions();

        return dto;
    }

    @Override
    public Map<String, MinionStatusDto> getNodeStatus() {
        if (minion.getMode() == MinionMode.CENTRAL) {
            throw new WebApplicationException("Cannot determine state of minions when running in central mode.");
        }
        RemoteService remote = minion.getSelf();
        MasterRootResource root = ResourceProvider.getResource(remote, MasterRootResource.class, null);
        return root.getMinions();
    }

}
