package io.bdeploy.ui.api;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("/attach-events")
public interface ManagedServersAttachEventResource {

    public static final String ATTACH_BROADCASTER = "AttachManagedBroadcast";

    @POST
    public void setLocalAttached(String groupName);

}
