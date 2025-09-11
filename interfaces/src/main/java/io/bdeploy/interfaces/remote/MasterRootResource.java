package io.bdeploy.interfaces.remote;

import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.minion.MultiNodeDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Master API. The master groups APIs available from minions and delegates tasks to them.
 */
@Path("/master")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterRootResource extends CommonUpdateResource {

    /**
     * @return the list of registered minions indexed by their name
     */
    @GET
    @Path("/minions")
    public Map<String, MinionStatusDto> getNodes();

    /**
     * @param name the name of the minion to add.
     * @param minion the minion configuration for the minion to add.
     */
    @PUT
    @Path("/minions/{name}")
    public void addServerNode(@PathParam("name") String name, RemoteService minion);

    /**
     * Attaches the given remote node to an already configured multi-node on this master.
     * <p>
     * The node will receive all the configuration already present for that multi-node just like a normal node after
     * registration.
     */
    @PUT
    @WeakTokenAllowed
    @Path("/minions/multi-node/{name}")
    public void attachMultiNode(@PathParam("name") String multiNodeName, MinionDto node);

    /**
     * @param name the name of the minion to add.
     * @param minion the configuration of a server which is currently STANDALONE or MANAGED. It will be migrated to a node.
     */
    @PUT
    @Path("/minions/{name}/migrate")
    public void convertNode(@PathParam("name") String name, RemoteService minion);

    /**
     * @param name the name of the minion to edit.
     * @param minion the updated minion configuration for the minion.
     */
    @POST
    @Path("/minions/{name}")
    public void editNode(@PathParam("name") String name, RemoteService minion);

    /**
     * @param name the name of the minion to edit.
     * @param minion the updated minion configuration for the minion.
     */
    @POST
    @Path("/minions/{name}/replace")
    public void replaceNode(@PathParam("name") String name, RemoteService minion);

    /**
     * @param name the name of the minion to remove.
     */
    @DELETE
    @Path("/minions/{name}")
    public void removeNode(@PathParam("name") String name);

    /**
     * @param name a single node to update
     * @param version the update to install
     * @param clean whether to clean up old versions
     */
    @POST
    @Path("/minions/{name}/update")
    public void updateNode(@PathParam("name") String name, Manifest.Key version, @QueryParam("clean") boolean clean);

    /**
     * @param name the name of the node to check.
     */
    @POST
    @Path("/minions/{name}/fsck")
    @RequiredPermission(permission = Permission.ADMIN)
    public Map<String, String> fsckNode(@PathParam("name") String name);

    /**
     * @param name the name of the node to prune.
     */
    @POST
    @Path("/minions/{name}/prune")
    @RequiredPermission(permission = Permission.ADMIN)
    public long pruneNode(@PathParam("name") String name);

    /**
     * @param name the name of the node to restart.
     */
    @POST
    @Path("/minions/{name}/restart")
    @RequiredPermission(permission = Permission.ADMIN)
    public void restartNode(@PathParam("name") String name);

    /**
     * @param name the name of the node to shutdown.
     */
    @POST
    @Path("/minions/{name}/shutdown")
    @RequiredPermission(permission = Permission.ADMIN)
    public void shutdownNode(@PathParam("name") String name);

    /**
     * Request the master that is responsible for the given named Hive.
     * <p>
     * Use {@link CommonRootResource#addInstanceGroup(InstanceGroupConfiguration, String)} to create new instance groups.
     *
     * @param name the name of a named hive.
     * @return the resource used to manage a certain namespace on the master
     */
    @Path("{name}")
    public MasterNamedResource getNamedMaster(@PathParam("name") String name);

    /**
     * @param name the name of the minion to add
     * @param config the data required for the multi-node
     */
    @PUT
    @Path("/minions/multi-nodes/{name}")
    public void addMultiNode(@PathParam("name") String name, MultiNodeDto config);

    /**
     * @param principal the principal name to issue the token to.
     * @return a "weak" token is suitable for applications that need to register themselves on master, like multi-nodes
     */
    @POST
    @Path("/weak-token")
    public String generateWeakToken(String principal);

}
