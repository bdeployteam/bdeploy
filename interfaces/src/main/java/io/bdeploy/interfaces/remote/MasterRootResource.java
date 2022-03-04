package io.bdeploy.interfaces.remote;

import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.minion.MinionStatusDto;
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
    public void addNode(@PathParam("name") String name, RemoteService minion);

    /**
     * @param name the name of the minion to edit.
     * @param minion the updated minion configuration for the minion.
     */
    @POST
    @Path("/minions/{name}")
    public void editNode(@PathParam("name") String name, RemoteService minion);

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
     * Request the master that is responsible for the given named Hive.
     * <p>
     * Use {@link CommonRootResource#addInstanceGroup(InstanceGroupConfiguration, String)} to create new instance groups.
     *
     * @param name the name of a named hive.
     * @return the resource used to manage a certain namespace on the master
     */
    @Path("{name}")
    public MasterNamedResource getNamedMaster(@PathParam("name") String name);

}
