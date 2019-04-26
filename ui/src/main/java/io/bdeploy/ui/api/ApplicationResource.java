package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
