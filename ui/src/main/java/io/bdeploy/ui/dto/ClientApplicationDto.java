package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Represents a client application.
 */
public class ClientApplicationDto {

    /** Unique identifier of the application */
    @JsonAlias("uuid")
    public String id;

    /** Custom user defined description */
    public String description;

    /** The operating system supported by the application */
    public OperatingSystem os;

}
