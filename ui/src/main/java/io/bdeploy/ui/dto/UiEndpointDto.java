package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;

/**
 * Represents a UI endpoint.
 */
public class UiEndpointDto {

    /** Unique identifier of the application */
    @JsonAlias("uuid")
    public String id;

    /**
     * @deprecated Compat with 4.x
     */
    @Deprecated(forRemoval = true)
    @JsonProperty("uuid")
    public String getUuid() {
        return id;
    }

    /** For display: the name of the application */
    public String appName;

    /** The operating system supported by the application */
    public HttpEndpoint endpoint;

}
