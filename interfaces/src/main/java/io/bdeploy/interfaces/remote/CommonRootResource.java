package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.Version;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/master") // compat with older MasterRootResource
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CommonRootResource {

    @GET
    @WeakTokenAllowed
    @Path("/version")
    public Version getVersion();

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
     * Returns a resource which can be used to query or access an instance.
     * <p>
     * Common resource also available on the central master.
     * <p>
     * Note: query parameter name <b>must</b> start with 'BDeploy_'
     *
     * @param group the instance group ID to get the instance resource for.
     * @return the {@link CommonInstanceResource} to query information from.
     */
    @Path("/common")
    public CommonInstanceResource getInstanceResource(@ActivityScope @QueryParam("BDeploy_group") String group);

    /**
     * Retrieves the current logger configuration.
     *
     * @return the configuration file.
     */
    @GET
    @Path("/logConfig")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public java.nio.file.Path getLoggerConfig();

    /**
     * Updates the log configuration on the master and all attached nodes.
     *
     * @param config the configuration file.
     */
    @POST
    @Path("/logConfig")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void setLoggerConfig(java.nio.file.Path config);

    /**
     * @return the contents of each nodes log directory. Optionally returns the content of a specified {@link BHive}'s log
     *         directory instead of the server log directory.
     */
    @GET
    @Path("/logFiles")
    public List<RemoteDirectory> getLogDirectories(@QueryParam("h") String hive);

    /**
     * Fetches the complete contents of a log file
     */
    @POST
    @Path("/logContent")
    public EntryChunk getLogContent(@QueryParam("m") String minion, RemoteDirectoryEntry entry, @QueryParam("o") long offset,
            @QueryParam("l") long limit);

    /**
     * Fetches the contents of a log file as stream.
     */
    @POST
    @Path("/logStream")
    public Response getLogStream(@QueryParam("m") String minion, RemoteDirectoryEntry entry);
}
