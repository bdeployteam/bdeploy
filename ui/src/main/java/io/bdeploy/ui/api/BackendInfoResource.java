package io.bdeploy.ui.api;

import java.util.Map;

import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.nodes.NodeListDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.BackendInfoDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
     * @deprecated use {@link #getNodeList()} instead.
     */
    @GET
    @Path("/minion-status")
    @Deprecated(since = "7.8.0", forRemoval = true)
    public Map<String, MinionStatusDto> getNodeStatus();

    /**
     * @return a list of all nodes along with additional information like mapping of runtime nodes to multi-nodes.
     */
    @GET
    @Path("/node-list")
    public NodeListDto getNodeList();

}
