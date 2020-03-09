package io.bdeploy.ui.api;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.bdeploy.jersey.ActivityScope;

@Path("/attach-events")
public interface ManagedServersAttachEventResource {

    public static final String ATTACH_BROADCASTER = "AttachManagedBroadcast";

    @POST
    public void setLocalAttached(@ActivityScope String groupName);

}
