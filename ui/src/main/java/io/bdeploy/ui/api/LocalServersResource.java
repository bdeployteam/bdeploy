package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

    /**
     * Used on a local server to manually (force) attach an instance group from a central server using it's encrypted
     * identification.
     * <p>
     * The central identification must be a string obtained using {@link #getCentralIdent(String, AttachIdentDto)} on the central
     * server.
     *
     * @return the name of the created (attached) instance group.
     */
    @PUT
    @Path("/manual-attach-central")
    @Consumes(MediaType.TEXT_PLAIN)
    public String manualAttachCentral(String central);

    /**
     * Used on a central server to retrieve an identification for the given instance group to manually attach on a local server
     * <p>
     * The return value is an encrypted and signed CentralIdentDto.
     */
    @POST
    @Path("/central-ident/{group}")
    public String getCentralIdent(@PathParam("group") String group, AttachIdentDto target);

    /**
     * Retrieve all available local servers for an instance group on the central server
     */
    @GET
    @Path("/list/{group}")
    public List<String> getServerNames(@PathParam("group") String instanceGroup);

    /**
     * Retrieve the controlling local server on the central server.
     */
    @GET
    @Path("/controlling-server/{group}/{instanceId}")
    public AttachIdentDto getServerForInstance(@PathParam("group") String instanceGroup,
            @PathParam("instanceId") String instanceId, @QueryParam("instanceTag") String instanceTag);

}
