package io.bdeploy.ui.api;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.ui.dto.ApplicationDto;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ApplicationResource {

    @GET
    public List<ApplicationDto> list();

    @GET
    @Path("/{name : .+}/{tag}/descriptor")
    public ApplicationDescriptor getDescriptor(@PathParam("name") String name, @PathParam("tag") String tag);

}
