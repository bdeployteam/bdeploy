package io.bdeploy.ui.api.impl;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.POST;

import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;

public class ManagedServersAttachEventResourceImpl implements ManagedServersAttachEventResource {

    public static final String ATTACH_BROADCASTER = "AttachManagedBroadcast";

    @Inject
    private JerseyScopeService jss;

    @Inject
    @Named(ATTACH_BROADCASTER)
    private JerseyEventBroadcaster bc;

    @Override
    @POST
    public void setLocalAttached(String groupName) {
        bc.send(groupName, jss.getScope());
    }

}
