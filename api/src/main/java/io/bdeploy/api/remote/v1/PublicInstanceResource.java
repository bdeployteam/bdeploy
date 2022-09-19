package io.bdeploy.api.remote.v1;

import java.util.SortedMap;

import io.bdeploy.api.remote.v1.dto.EndpointsConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;
import io.bdeploy.bhive.model.Manifest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface PublicInstanceResource {

    /**
     * Returns a list of instance configurations available in this hive.
     *
     * @return the list of instance configurations.
     */
    @Operation(summary = "Get Instance Configurations",
               description = "Retrieve the configuration for all instances in the instance group.")
    @GET
    @Path("/instances")
    public SortedMap<Manifest.Key, InstanceConfigurationApi> listInstanceConfigurations(
            @Parameter(description = "Whether to return only the latest configuration of each instance") @QueryParam("latest") boolean latestOnly);

    /**
     * @param instanceId the instance to query
     * @return the list of endpoints provided by the product in the active instance version
     */
    @Operation(summary = "Get Available Endpoints",
               description = "Retrieve a list of all endpoints exposed by applications in the given instance.")
    @GET
    @Path("/endpoints")
    public SortedMap<String, EndpointsConfigurationApi> getAllEndpoints(
            @Parameter(description = "The ID of the instance to query") @QueryParam("BDeploy_instance") String instanceId);

    /**
     * Get a resource which allows to proxy various calls to the target application provided endpoint.
     * <p>
     * Note: query parameter name <b>must</b> start with 'BDeploy_'
     *
     * @param instanceId the instance ID
     * @param applicationId the application ID
     */
    @Operation
    @Path("/proxy")
    public PublicProxyResource getProxyResource(
            @Parameter(description = "The ID of the instance to proxy to") @QueryParam("BDeploy_instance") String instanceId,
            @Parameter(description = "The ID of the application to proxy to") @QueryParam("BDeploy_application") String applicationId);

}
