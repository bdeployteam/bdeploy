package io.bdeploy.jersey.resources;

import java.util.List;

import io.bdeploy.jersey.actions.ActionBroadcastDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/actions")
@Produces(MediaType.APPLICATION_JSON)
public interface ActionResource {

    @GET
    public List<ActionBroadcastDto> getActions(@QueryParam("group") String group, @QueryParam("instance") String instance);

}
