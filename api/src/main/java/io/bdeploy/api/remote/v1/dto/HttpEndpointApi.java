package io.bdeploy.api.remote.v1.dto;

public class HttpEndpointApi {

    /**
     * The unique ID of the endpoint.
     */
    public String id;

    /**
     * The path to the endpoint on the server.
     * <p>
     * Note that path parameters are currently not supported.
     */
    public String path;

}
