package io.bdeploy.interfaces.remote;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Proxy requests to a target process on an instance.
 * <p>
 * A request made on any of the resource methods is captured, wrapped, forwarded and executed on the target system. The response
 * (including all headers, types, etc.) is routed back to the caller.
 * Example how to use this using cURL on the command line:
 *
 * <pre>
 * $ curl -v 'https://localhost:7705/api/master/common/endpoints?BDeploy_group=[GROUP]&BDeploy_instance=[INSTANCE]' -H 'Accept: application/json, text/plain' -H 'Authorization: Bearer [TOKEN]' --insecure
 * $ curl -v 'https://localhost:7705/api/master/common/proxy/[ENDPOINTID]?BDeploy_group=[GROUP]&BDeploy_instance=[INSTANCE]&BDeploy_application=[APPLICATION]' -H 'Accept: application/json, text/plain' -H 'X-BDeploy-Authorization: Bearer [TOKEN]' --insecure -u [USER]:[PASS]
 * </pre>
 *
 * @see ProxiedRequestWrapper Forwarded request details (headers, parameters, body, ...).
 */
@Consumes("*/*")
@Produces("*/*")
public interface CommonProxyResource {

    @HEAD
    @Path("{endpoint : .+}")
    public Response head(@PathParam("endpoint") String endpointId);

    @OPTIONS
    @Path("{endpoint : .+}")
    public Response options(@PathParam("endpoint") String endpointId);

    @GET
    @Path("{endpoint : .+}")
    public Response get(@PathParam("endpoint") String endpointId);

    @PUT
    @Path("{endpoint : .+}")
    public Response put(@PathParam("endpoint") String endpointId, byte[] body);

    @POST
    @Path("{endpoint : .+}")
    public Response post(@PathParam("endpoint") String endpointId, byte[] body);

    @DELETE
    @Path("{endpoint : .+}")
    public Response delete(@PathParam("endpoint") String endpointId);

    @PATCH
    @Path("{endpoint : .+}")
    public Response patch(@PathParam("endpoint") String endpointId, byte[] body);
}
