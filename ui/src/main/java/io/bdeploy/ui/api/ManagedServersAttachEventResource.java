package io.bdeploy.ui.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.bdeploy.jersey.Scope;

@Path("/attach-events")
public interface ManagedServersAttachEventResource {

    @POST
    public void setLocalAttached(@Scope String groupName);

}
