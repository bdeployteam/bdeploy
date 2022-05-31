package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;

/**
 * Represents a UI endpoint.
 */
public class UiEndpointDto {

    /** Unique identifier of the application */
    public String uuid;

    /** The operating system supported by the application */
    public HttpEndpoint endpoint;

}
