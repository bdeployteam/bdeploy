package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.ui.ControllingMaster;
import io.bdeploy.ui.ManagedMasters;
import io.bdeploy.ui.ManagedMastersConfiguration;
import io.bdeploy.ui.api.MasterProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.ManagedMasterDto;

public class UiMasterProvider implements MasterProvider {

    @Inject
    private Minion minion;

    @Override
    public RemoteService getControllingMaster(BHive hive, Manifest.Key imKey) {
        switch (minion.getMode()) {
            case CENTRAL:
                ManagedMastersConfiguration available = new ManagedMasters(hive).read();
                String associated = new ControllingMaster(hive, imKey).read().getName();
                if (associated == null) {
                    throw new WebApplicationException("Cannot find associated master for instance " + imKey,
                            Status.EXPECTATION_FAILED);
                }
                ManagedMasterDto controlling = available.getManagedMaster(associated);
                if (controlling == null) {
                    throw new WebApplicationException(
                            "Recorded master for instance " + imKey + " not longer available: " + associated,
                            Status.EXPECTATION_FAILED);
                }
                return new RemoteService(UriBuilder.fromUri(controlling.uri).build(), controlling.auth);
            case SLAVE:
                throw new UnsupportedOperationException("A slave may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

}
