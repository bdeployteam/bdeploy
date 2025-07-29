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
import io.bdeploy.ui.api.MinionMode;
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
                    throw new WebApplicationException("Recorded master for " + assetKey + " no longer available: " + associated,
                            Status.EXPECTATION_FAILED);
                }
                return new RemoteService(UriBuilder.fromUri(controlling.uri).build(), controlling.auth);
            case NODE:
                throw new UnsupportedOperationException("A node may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

    @Override
    public RemoteService getNamedMasterOrSelf(BHive hive, String target) {
        if (minion.getMode() == MinionMode.CENTRAL) {
            ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
            ManagedMasterDto server = masters.getManagedMaster(target);
            if (server == null) {
                throw new WebApplicationException("Managed server not found: " + target, Status.NOT_FOUND);
            }

            return new RemoteService(UriBuilder.fromUri(server.uri).build(), server.auth);
        } else {
            return minion.getSelf();
        }
    }

}
