package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.jersey.ActivityScope;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@OpenAPIDefinition(info = @Info(title = "BDeploy Public Master API", description = "BDeploy backend APIs for public use. "
        + "Callers must set the X-BDeploy-Authorization header to be able to access APIs. "
        + "This token can be obtained through the CLI and the Web UI. "
        + "The API is exposed on any BDeploy master (regardless of its mode) on the '/api/' namespace (e.g. 'https://localhost:7701/api/master/...')"),
                   security = { @SecurityRequirement(name = "X-BDeploy-Authorization") })
@Path("/master") // compat with older MasterRootResource
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CommonRootResource {

    /**
     * Software repository hives contain additional software which can be referenced when building products.
     *
     * @return the list of available software repository hives on the master.
     */
    @Operation(summary = "Get Software Repositories",
               description = "Retrieve a list of all available Software Repositories which may be used to resolve product dependencies at build time.")
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
    @Operation(summary = "Get Instance Groups", description = "Retrieve a list of all available Instance Groups on the server.")
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
    @Operation
    @Path("/common")
    public CommonInstanceResource getInstanceResource(
            @Parameter(description = "The name of the instance group to access") @ActivityScope @QueryParam("BDeploy_group") String group);

}
