package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.ui.dto.AttachIdentDto;

@Path("/local-servers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface LocalServersResource {

    /**
     * Used on a central server to auto-attach a local server
     */
    @PUT
    @Path("/auto-attach/{group}")
    public void tryAutoAttach(@PathParam("group") String groupName, AttachIdentDto target);

    /**
     * Used on a central server to manually (force) attach the given server without verification
     */
    @PUT
    @Path("/manual-attach/{group}")
    public void manualAttach(@PathParam("group") String groupName, AttachIdentDto target);

    @GET
    @Path("/list/{group}")
    public List<String> getServerNames(@PathParam("group") String instanceGroup);

    @GET
    @Path("/controlling-server/{group}/{instanceId}")
    public AttachIdentDto getServerForInstance(@PathParam("group") String instanceGroup,
            @PathParam("instanceId") String instanceId, @QueryParam("instanceTag") String instanceTag);

}
