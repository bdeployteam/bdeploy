package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public class HttpEndpoint {

    public enum HttpEndpointType {
        @JsonEnumDefaultValue
        DEFAULT,
        PROBE_STARTUP,
        PROBE_ALIVE,
    }

    public enum HttpAuthenticationType {
        @JsonEnumDefaultValue
        NONE,
        BASIC,
        DIGEST
    }

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

    /**
     * The port running the service. This is usually a reference to a configuration parameter of the hosting application
     * <p>
     * This is a candidate for <b>actual</b> configuration on the application, right now this cannot be configured
     */
    public String port;

    /**
     * Use HTTPS to connect
     */
    public boolean secure = false;

    /**
     * Trust all HTTPS certificates
     */
    public boolean trustAll = false;

    /**
     * Path to a trust store which contains the certificate(s) to use when calling the endpoint.
     * <p>
     * The trust store must be in JKS format
     */
    public String trustStore;

    /**
     * Password for the trust store.
     */
    public String trustStorePass;

    /**
     * The authentication type to use when performing the request
     */
    public HttpAuthenticationType authType = HttpAuthenticationType.NONE;

    /**
     * The user to use to perform authentication of any request
     */
    public String authUser;

    /**
     * The password to use to perform authentication of any request
     */
    public String authPass;

    /**
     * The type of the endpoint.
     */
    public HttpEndpointType type = HttpEndpointType.DEFAULT;

}
