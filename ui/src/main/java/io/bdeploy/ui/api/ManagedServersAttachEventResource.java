package io.bdeploy.ui.api;

import io.bdeploy.jersey.Scope;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/attach-events")
public interface ManagedServersAttachEventResource {

    @POST
    public void setLocalAttached(@Scope String groupName);

}
