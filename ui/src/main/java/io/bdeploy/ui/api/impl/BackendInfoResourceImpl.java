package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.AttachIdentDto;
import io.bdeploy.ui.dto.BackendInfoDto;

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
    public AttachIdentDto getAttachIdentification() {
        AttachIdentDto dto = new AttachIdentDto();

        dto.name = minion.getSelfName();
        dto.auth = minion.getSelf().getAuthPack();
        dto.uri = info.getBaseUri().toString();

        return dto;
    }

}
