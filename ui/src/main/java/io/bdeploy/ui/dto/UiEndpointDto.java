package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;

/**
 * Represents a UI endpoint.
 */
public class UiEndpointDto {

    /** Unique identifier of the application */
    @JsonAlias("uuid")
    public String id;

    /** For display: the name of the application */
    public String appName;

    /** The operating system supported by the application */
    public HttpEndpoint endpoint;

    /** The pre-resolved value of {@link HttpEndpoint#enabled} */
    public boolean endpointEnabledPreresolved;
}
