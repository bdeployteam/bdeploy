package io.bdeploy.ui.api;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.bdeploy.jersey.ActivityScope;

@Path("/attach-events")
public interface ManagedServersAttachEventResource {

    @POST
    public void setLocalAttached(@ActivityScope String groupName);

}
