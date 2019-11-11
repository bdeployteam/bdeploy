package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.minion.MinionStatusDto;

/**
 * Master API. The master groups APIs available from minions and delegates tasks to them.
 */
@Path("/master")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterRootResource {

    /**
     * @return the list of registered minions indexed by their name
     */
    @GET
    @Path("/minions")
    public SortedMap<String, MinionStatusDto> getMinions();

    /**
     * Update all minions one after another, and (last) the master minion.
     *
     * @param version update to the given {@link Key}. The {@link Key} must have been pushed to the default hive before.
     * @param clean whether to clean up old versions.
     */
    @PUT
    @Path("/update")
    public void update(Manifest.Key version, @QueryParam("clean") boolean clean);

    /**
     * Software repository hives contain additional software which can be referenced when building products.
     *
     * @return the list of available software repository hives on the master.
     */
    @GET
    @Path("/softwareRepositories")
    public List<SoftwareRepositoryConfiguration> getSoftwareRepositories();

    /**
     * Add a new software repository at the given location.
     *
     * @param config the software repository meta-data
     * @param storage the storage location where to put the new software repository
     */
    @PUT
    @Path("/softwareRepositories")
    public void addSoftwareRepository(SoftwareRepositoryConfiguration config, @QueryParam("storage") String storage);

    /**
     * The list of storage locations on the remote can be used to prompt the user where to create a named hive if there are
     * multiple locations.
     *
     * @return a list of available storage locations.
     */
    @GET
    @Path("/storage")
    public Set<String> getStorageLocations();

    /**
     * Creates a named hive on the remote
     *
     * @param meta the {@link InstanceGroupConfiguration} to associate with the new hive
     * @param storage the location to create the hive in.
     */
    @PUT
    @Path("/instanceGroups")
    public void addInstanceGroup(InstanceGroupConfiguration meta, @QueryParam("storage") String storage);

    /**
     * @param name the name of the Instance Group to delete.
     */
    @DELETE
    @Path("/instanceGroups")
    public void deleteInstanceGroup(@QueryParam("name") String name);

    /**
     * Software repository hives contain additional software which can be referenced when building products.
     *
     * @return the list of available software repository hives on the master.
     */
    @GET
    @Path("/instanceGroups")
    public List<InstanceGroupConfiguration> getInstanceGroups();

    /**
     * Request the master that is responsible for the given named Hive.
     * <p>
     * Use {@link #addInstanceGroup(InstanceGroupConfiguration, String)} to create new instance groups.
     *
     * @param name the name of a named hive.
     * @return the resource used to manage a certain namespace on the master
     */
    @Path("{name}")
    public MasterNamedResource getNamedMaster(@PathParam("name") String name);

}
