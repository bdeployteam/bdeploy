package io.bdeploy.ui.api.impl;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.ui.api.BackendInfoResource;

public class BackendInfoResourceImpl implements BackendInfoResource {

    @Override
    public String getVersion() {
        return VersionHelper.readVersion();
    }

}
