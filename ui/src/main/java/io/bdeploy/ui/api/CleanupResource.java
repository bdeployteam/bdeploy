package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.cleanup.CleanupGroup;

@Path("/cleanUi")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CleanupResource {

    @GET
    public List<CleanupGroup> calculate();

    @POST
    public void perform(List<CleanupGroup> groups);

}
