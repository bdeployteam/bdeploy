package io.bdeploy.ui.api;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.BackendInfoDto;

@Path("/backend-info")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BackendInfoResource {

    @GET
    @Unsecured
    @Path("/version")
    public BackendInfoDto getVersion();

    /**
     * @return a DTO which can be used to attach this server to another server.
     */
    @GET
    @Path("/managed-master")
    public ManagedMasterDto getManagedMasterIdentification();

    /**
     * Requests the runtime state of all nodes.
     */
    @GET
    @Path("/minion-status")
    public Map<String, MinionStatusDto> getNodeStatus();

}
