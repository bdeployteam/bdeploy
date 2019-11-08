package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.ui.ManagedMasterAssociationMetaManifest;
import io.bdeploy.ui.ManagedMasterAttachmentsMetaManifest;
import io.bdeploy.ui.api.MasterProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.AttachIdentDto;

public class UiMasterProvider implements MasterProvider {

    @Inject
    private Minion minion;

    @Override
    public RemoteService getControllingMaster(BHive hive, Manifest.Key imKey) {
        switch (minion.getMode()) {
            case CENTRAL:
                ManagedMasterAttachmentsMetaManifest available = ManagedMasterAttachmentsMetaManifest.read(hive);
                String associated = ManagedMasterAssociationMetaManifest.read(hive, imKey).getMasterName();
                if (associated == null) {
                    throw new IllegalStateException("Cannot find associated master for instance " + imKey);
                }
                AttachIdentDto controlling = available.getAttachedManagedServers().get(associated);
                return new RemoteService(UriBuilder.fromUri(controlling.uri).build(), controlling.auth);
            case SLAVE:
                throw new UnsupportedOperationException("A slave may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

}
