package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.minion.MinionStatusDto;
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
     */
    @GET
    @Path("/nodes")
    public Map<String, MinionStatusDto> getNodes();

    /**
     * @param name the name of the new node to add
     * @param node the remote configuration (URI, auth).
     */
    @PUT
    @Path("/nodes/{name}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void addNode(@PathParam("name") String name, RemoteService node);

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
    @Path("/nodes/{name}/fsck")
    @RequiredPermission(permission = Permission.ADMIN)
    public Map<String, String> fsckNode(@PathParam("name") String name);

    /**
     * @param name the name of the node to prune.
     */
    @POST
    @Path("/nodes/{name}/prune")
    @RequiredPermission(permission = Permission.ADMIN)
    public long pruneNode(@PathParam("name") String name);

}
