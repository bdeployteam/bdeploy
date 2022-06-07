package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public class HttpEndpoint {

    public enum HttpEndpointType {
        @JsonEnumDefaultValue
        DEFAULT,
        PROBE_STARTUP,
        PROBE_ALIVE,
        UI,
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
     * The root path to the endpoint on the server.
     * <p>
     * Note that path parameters are currently not supported.
     */
    public String path;

    /**
     * Additional path segment which should be appended to the endpoint path given.
     * <p>
     * Any request to any endpoint will always include {path}/{contextPath}/{subPath} where subPath are any
     * trailing path segments given in the actual request.
     * <p>
     * Using contextPath instead of path can be important to applications which use relative paths to resolve additional
     * resources.
     */
    public String contextPath;

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

    /**
     * Whether the endpoint hosts a Web UI which can be proxied.
     * <p>
     * The proxying feature (e.g. for inlining, or displaying across networks) requires web application to be
     * <ul>
     * <li>Location agnostic - the URL presented to the browser is *not* the URL where the UI is hosted at, it is instead
     * rewritten.
     * <li>Limited to simple request/response models, i.e. no WebSockets.
     * </ul>
     * <p>
     * Currently, this flag is only evaluated for endpoints of type UI.
     */
    public boolean proxying = false;

}
