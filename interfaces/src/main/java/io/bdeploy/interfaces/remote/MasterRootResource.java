package io.bdeploy.interfaces.remote;

import java.util.SortedMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.minion.MinionStatusDto;

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
    public SortedMap<String, MinionStatusDto> getMinions();

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
