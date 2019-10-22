package io.bdeploy.ui.api.impl;

import javax.inject.Inject;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.ui.api.MasterProvider;
import io.bdeploy.ui.api.Minion;

public class UiMasterProvider implements MasterProvider {

    @Inject
    private Minion minion;

    @Override
    public RemoteService getControllingMaster(BHive hive, Manifest.Key imKey) {
        switch (minion.getMode()) {
            case CENTRAL:
                // find meta information or die. each instance must have an attached local server. all local
                // servers are configured on instance group level (in the given BHive).
                throw new UnsupportedOperationException("Central mode note yet implemented");
            case SLAVE:
                throw new UnsupportedOperationException("A slave may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

}
