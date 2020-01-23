package io.bdeploy.interfaces.descriptor.application;

public class HttpEndpoint {

    /**
     * The unique ID of the endpoint.
     */
    public String id;

    /**
     * Use HTTPS to connect
     */
    public boolean secure = false;

    /**
     * Trust all HTTPS certificates
     */
    public boolean trustAll = false;

    /**
     * The port running the service. This is usually a reference to a configuration parameter of the hosting application
     * <p>
     * This is a candidate for <b>actual</b> configuration on the application, right now this cannot be configured
     */
    public String port;

    /**
     * The path to the endpoint on the server.
     * <p>
     * Note that path parameters are currently not supported.
     */
    public String path;

}
