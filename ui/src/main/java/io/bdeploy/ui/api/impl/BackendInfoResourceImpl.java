package io.bdeploy.ui.api.impl;

import javax.inject.Inject;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.BackendInfoDto;

public class BackendInfoResourceImpl implements BackendInfoResource {

    @Inject
    public Minion minion;

    @Override
    public BackendInfoDto getVersion() {
        return new BackendInfoDto(VersionHelper.readVersion(), minion.getMode());
    }

}
