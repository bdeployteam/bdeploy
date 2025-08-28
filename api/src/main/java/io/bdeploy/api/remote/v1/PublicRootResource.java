package io.bdeploy.api.remote.v1;

import java.util.List;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;
import io.bdeploy.jersey.Scope;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@OpenAPIDefinition(info = @Info(title = "BDeploy Public Master API", version = "1",
                                description = "BDeploy backend APIs for public use. "
                                        + "Callers must set the X-BDeploy-Authorization header to be able to access APIs. "
                                        + "This token can be obtained through the CLI and the Web UI. "
                                        + "The API is exposed on any BDeploy master (regardless of its mode) on the '/api' namespace (e.g. 'https://localhost:7701/api/public/v1/...')"))
@Path("/public/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface PublicRootResource {

    /**
     * @return the currently running server version
     */
    @Operation(summary = "Get BDeploy Server Version", description = "Retrieve the version of the running BDeploy server.")
    @GET
    @WeakTokenAllowed
    @Path("/version")
    public String getVersion();

    /**
     * @param user the user to log in
     * @param pass the password to use.
     * @param full whether a full authentication pack or only the token part is requested.
     * @return Either a full authentication pack or a token only.
     * @deprecated Causes problems with encoding of query parameters with certain strong password values.
     */
    @Deprecated(since = "2.3.0")
    @Operation(summary = "Login to a BDeploy Server",
               description = "Uses given credentials to create a token for the BDeploy server. This token can be used by tooling to perform communication with the server on behalf of the given user. The 'full' parameter controls the type of token returned. Most tools require a full token.")
    @GET
    @Unsecured
    @Path("/login")
    public Response login(@QueryParam("user") String user, @QueryParam("pass") String pass, @QueryParam("full") boolean full);

    /**
     * This new variant of the login method avoids query parameters to avoid issues with encoding.
     *
     * @param credentials the user and password DTO.
     * @param full whether a full authentication pack or only the token part is requested.
     * @return Either a full authentication pack or a token only.
     */
    @Operation(summary = "Login to a BDeploy Server",
               description = "Uses given credentials to create a token for the BDeploy server. This token can be used by tooling to perform communication with the server on behalf of the given user. The 'full' parameter controls the type of token returned. Most tools require a full token. This new avoids query parameters.")
    @POST
    @Unsecured
    @Path("/login2")
    public Response login2(CredentialsApi credentials, @QueryParam("full") boolean full);

    /**
     * Software repository hives contain additional software which can be referenced when building products.
     *
     * @return the list of available software repository hives on the master.
     */
    @Operation(summary = "Get Software Repositories",
               description = "Retrieve a list of all available Software Repositories which may be used to resolve product dependencies at build time.")
    @GET
    @Path("/softwareRepositories")
    public List<SoftwareRepositoryConfigurationApi> getSoftwareRepositories();

    /**
     * Software repository hives contain additional software which can be referenced when building products.
     *
     * @return the list of available software repository hives on the master.
     */
    @Operation(summary = "Get Instance Groups", description = "Retrieve a list of all available Instance Groups on the server.")
    @GET
    @Path("/instanceGroups")
    public List<InstanceGroupConfigurationApi> getInstanceGroups();

    /**
     * @param instanceId - the instance ID to get the instance group for.
     * @return the {@link InstanceGroupConfigurationApi} of the instance group containing the instance with the given instance ID.
     */
    @Operation(summary = "Get instance group by instance ID",
               description = "Get instance group containing instance with the given instance ID")
    @GET
    @Path("/groupForInstance")
    public InstanceGroupConfigurationApi getInstanceGroupByInstanceId(@QueryParam("instanceId") String instanceId);

    /**
     * Returns a resource which can be used to query or access an instance.
     * <p>
     * Common resource also available on the central master.
     * <p>
     * Note: query parameter name <b>must</b> start with 'BDeploy_'
     *
     * @param group the instance group ID to get the instance resource for.
     * @return the {@link PublicInstanceResource} to query information from.
     */
    @Operation
    @Path("/common")
    public PublicInstanceResource getInstanceResource(
            @Parameter(description = "The name of the instance group to access") @Scope @QueryParam("BDeploy_group") String group);

}
