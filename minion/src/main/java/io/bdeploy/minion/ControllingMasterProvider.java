package io.bdeploy.minion;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.ui.api.Minion;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

public class ControllingMasterProvider implements MasterProvider {

    @Inject
    private Minion minion;

    @Override
    public RemoteService getControllingMaster(BHive hive, Manifest.Key assetKey) {
        switch (minion.getMode()) {
            case CENTRAL:
                ManagedMastersConfiguration available = new ManagedMasters(hive).read();
                String associated = new ControllingMaster(hive, assetKey).read().getName();
                if (associated == null) {
                    throw new WebApplicationException("Cannot find associated master for " + assetKey, Status.EXPECTATION_FAILED);
                }
                ManagedMasterDto controlling = available.getManagedMaster(associated);
                if (controlling == null) {
                    throw new WebApplicationException("Recorded master for " + assetKey + " not longer available: " + associated,
                            Status.EXPECTATION_FAILED);
                }
                return new RemoteService(UriBuilder.fromUri(controlling.uri).build(), controlling.auth);
            case NODE:
                throw new UnsupportedOperationException("A node may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

}
