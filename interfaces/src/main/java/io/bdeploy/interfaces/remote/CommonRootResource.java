package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.common.Version;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;

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
     * Updates the log configuration on the master and all attached nodes.
     *
     * @param config the configuration file.
     */
    @POST
    @Path("/logConfig")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void setLoggerConfig(java.nio.file.Path config);

    /**
     * @return the contents of each nodes log directory.
     */
    @GET
    @Path("/logFiles")
    public List<RemoteDirectory> getLogDirectories();

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
