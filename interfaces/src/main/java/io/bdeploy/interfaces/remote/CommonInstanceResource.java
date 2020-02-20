package io.bdeploy.interfaces.remote;

import java.util.SortedMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CommonInstanceResource {

    /**
     * Returns a list of instance configurations available in this hive.
     *
     * @return the list of instance configurations.
     */
    @GET
    @Path("/instances")
    public SortedMap<Manifest.Key, InstanceConfiguration> listInstanceConfigurations(@QueryParam("latest") boolean latestOnly);

    /**
     * @param instanceId the instance to query
     * @return the list of endpoints provided by the product in the active instance version
     */
    @GET
    @Path("/endpoints")
    public SortedMap<String, EndpointsConfiguration> getAllEndpoints(@QueryParam("BDeploy_instance") String instanceId);

    /**
     * Get a resource which allows to proxy various calls to the target application provided endpoint.
     * <p>
     * Note: query parameter name <b>must</b> start with 'BDeploy_'
     *
     * @param instanceId the instance ID
     * @param applicationId the application ID
     */
    @Path("/proxy")
    public CommonProxyResource getProxyResource(@QueryParam("BDeploy_instance") String instanceId,
            @QueryParam("BDeploy_application") String applicationId);

    @POST
    @Path("/forwardProxy")
    public ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper);

}
