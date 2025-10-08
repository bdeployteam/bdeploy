package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.nodes.NodeListDto;
import io.bdeploy.ui.dto.CreateMultiNodeDto;
import io.bdeploy.ui.dto.NodeAttachDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The node management resource for the local master server.
 * <p>
 * Provides administrative abilities to the UIs (add/remove/...).
 */
@Path("/node-admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NodeManagementResource {

    /**
     * @return the current node status.
     * @deprecated only there for compat with the old CLI
     */
    @GET
    @Path("/nodes")
    @Deprecated(since = "7.8.0", forRemoval = true)
    public Map<String, MinionStatusDto> getNodes();

    /**
     * @return the current node status.
     */
    @GET
    @Path("/node-list")
    public NodeListDto getNodeList();

    /**
     * @param data the attach data including remote configuration (URI, auth).
     */
    @PUT
    @Path("/nodes")
    @RequiredPermission(permission = Permission.ADMIN)
    public void addServerNode(NodeAttachDto data);

    /**
     * @param name the name of the node to edit
     * @param node the updated remote configuration.
     */
    @POST
    @Path("/nodes/{name}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void editNode(@PathParam("name") String name, RemoteService node);

    /**
     * @param name the name of the node to be removed.
     */
    @DELETE
    @Path("/nodes/{name}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void removeNode(@PathParam("name") String name);

    /**
     * @param name the name of the node to apply an update to.
     * @param target the target keys to install on the node.
     */
    @POST
    @Path("/nodes/{name}/update")
    @RequiredPermission(permission = Permission.ADMIN)
    public void updateNode(@PathParam("name") String name, List<Key> target);

    /**
     * @param name the name of the node to apply an update to.
     * @param node the new node's configuration.
     */
    @POST
    @Path("/nodes/{name}/replace")
    @RequiredPermission(permission = Permission.ADMIN)
    public void replaceNode(@PathParam("name") String name, RemoteService node);

    /**
     * @param name the name of the node to check.
     */
    @POST
    @Path("/nodes/{name}/repair-and-prune")
    @RequiredPermission(permission = Permission.ADMIN)
    public RepairAndPruneResultDto repairAndPruneNode(@PathParam("name") String name);

    @POST
    @Path("/nodes/{name}/restart")
    @RequiredPermission(permission = Permission.ADMIN)
    public void restartNode(@PathParam("name") String name);

    @POST
    @Path("/nodes/{name}/shutdown")
    @RequiredPermission(permission = Permission.ADMIN)
    public void shutdownNode(@PathParam("name") String name);

    /**
     * @param createMultiNodeDto the required data for declaring a multi-node
     */
    @PUT
    @Path("/multi-nodes")
    @RequiredPermission(permission = Permission.ADMIN)
    public void addMultiNode(CreateMultiNodeDto createMultiNodeDto);

    /**
     * @param name the name of the multi-node for which to generate the file
     */
    @GET
    @Path("/multi-nodes/{name}/masterFile")
    public MultiNodeMasterFile getMultiNodeMasterFile(@PathParam("name") String name);
}
