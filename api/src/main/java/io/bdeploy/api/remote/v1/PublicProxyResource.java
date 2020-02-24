package io.bdeploy.api.remote.v1;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Proxy requests to a target process on an instance.
 * <p>
 * A request made on any of the resource methods is captured, wrapped, forwarded and executed on the target system. The response
 * (including all headers, types, etc.) is routed back to the caller.
 * Example how to use this using cURL on the command line:
 *
 * <pre>
 * $ curl -v 'https://localhost:7705/api/public/v1/common/endpoints?BDeploy_group=[GROUP]&BDeploy_instance=[INSTANCE]' -H 'Accept: application/json, text/plain' -H 'Authorization: Bearer [TOKEN]' --insecure
 * $ curl -v 'https://localhost:7705/api/public/v1/common/proxy/[ENDPOINTID]?BDeploy_group=[GROUP]&BDeploy_instance=[INSTANCE]&BDeploy_application=[APPLICATION]' -H 'Accept: application/json, text/plain' -H 'X-BDeploy-Authorization: Bearer [TOKEN]' --insecure -u [USER]:[PASS]
 * </pre>
 */
@Consumes("*/*")
@Produces("*/*")
public interface PublicProxyResource {

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @HEAD
    @Path("{endpoint : .+}")
    public Response head(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @OPTIONS
    @Path("{endpoint : .+}")
    public Response options(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @GET
    @Path("{endpoint : .+}")
    public Response get(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @PUT
    @Path("{endpoint : .+}")
    public Response put(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId,
            byte[] body);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @POST
    @Path("{endpoint : .+}")
    public Response post(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId,
            byte[] body);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @DELETE
    @Path("{endpoint : .+}")
    public Response delete(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId);

    @Operation(summary = "Proxy the request to the target process.", responses = {
            @ApiResponse(description = "The response of the target process is mapped back, including headers (content type, etc.)."),
            @ApiResponse(responseCode = "412",
                         description = "In case the endpoint cannot be resolved/called (instance not found, application not found, application not running, ...). See status message for problem details.") })
    @PATCH
    @Path("{endpoint : .+}")
    public Response patch(
            @Parameter(description = "ID of the endpoint exposed by the given application in the given instance.") @PathParam("endpoint") String endpointId,
            byte[] body);
}
