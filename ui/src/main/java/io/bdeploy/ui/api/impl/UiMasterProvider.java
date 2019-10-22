package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.ui.LocalMasterAssociationMetaManifest;
import io.bdeploy.ui.LocalMasterAttachmentsMetaManifest;
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
                LocalMasterAttachmentsMetaManifest available = LocalMasterAttachmentsMetaManifest.read(hive);
                String associated = LocalMasterAssociationMetaManifest.read(hive, imKey);
                if (associated == null) {
                    throw new IllegalStateException("Cannot find associated master for instance " + imKey);
                }
                AttachIdentDto controlling = available.getAttachedLocalServers().get(associated);
                return new RemoteService(UriBuilder.fromUri(controlling.uri).build(), controlling.auth);
            case SLAVE:
                throw new UnsupportedOperationException("A slave may never require remote communication with a master");
            default:
                return minion.getSelf();
        }
    }

}
