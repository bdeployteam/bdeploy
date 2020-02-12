package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;

import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;

public class ManagedServersAttachEventResourceImpl implements ManagedServersAttachEventResource {

    @Inject
    @Named(ATTACH_BROADCASTER)
    private JerseyEventBroadcaster bc;

    @Override
    @POST
    public void setLocalAttached(String groupName) {
        bc.send(groupName);
    }

}
