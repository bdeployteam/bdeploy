package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

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

    @JsonPropertyDescription("The unique ID of the endpoint. This ID is later used to reference this endpoint when proxying.")
    public String id;

    @JsonPropertyDescription("The root path of the endpoint on the server.")
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
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("An additional context path which should be appended to the path to the endpoint. This is used to provide additional path segments for UI endpoints.")
    public LinkedValueConfiguration contextPath = new LinkedValueConfiguration(null);

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("The port where the endpoint can be found. Typically this is using link expressions to reference an application parameter, e.g. '{{V:http-port}}'")
    public LinkedValueConfiguration port = new LinkedValueConfiguration(null);

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("Whether the endpoint is using HTTPS instead of HTTP, defaults to 'false'")
    public LinkedValueConfiguration secure = new LinkedValueConfiguration("false");

    @JsonPropertyDescription("Whether to trust all (i.e. self signed) certificates, defaults to 'false'")
    public boolean trustAll = false;

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("Path to a trust store which contains additional certificates to trust.")
    public LinkedValueConfiguration trustStore = new LinkedValueConfiguration(null);

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("The passphrase for the trust store")
    public LinkedValueConfiguration trustStorePass = new LinkedValueConfiguration(null);

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("The authentication scheme to be used when contacting the endpoint from within BDeploy (proxying)")
    public LinkedValueConfiguration authType = new LinkedValueConfiguration(HttpAuthenticationType.NONE.name());

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("The username to pass using the specified authentication scheme.")
    public LinkedValueConfiguration authUser = new LinkedValueConfiguration(null);

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonPropertyDescription("The password to pass using the specified authentication scheme.")
    public LinkedValueConfiguration authPass = new LinkedValueConfiguration(null);

    @JsonPropertyDescription("The type of the endpoint. Defaults to 'DEFAULT', i.e. a generic endpoint. 'UI' endpoints are presented to the user in a way similar to client applications. 'PROBE' endpoints are used internally to verify process state.")
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
    @JsonPropertyDescription("Specific to 'UI' type endpoints, defines whether the UI on this endpoint can be used using BDeploys proxying feature (i.e. embed the target UI in BDeploy even accross network boundaries).")
    public boolean proxying = false;

}
