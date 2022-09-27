package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ApplicationExitCodeDescriptor {

    /**
     * Exit code indicating that the application requests to update itself.
     * <p>
     * This exit code is only evaluated in case of a client application. The launcher
     * will install available updates and restart the application.
     * </p>
     */
    @JsonPropertyDescription("Defines the exit code which indicates to the client launcher that the application wishes to be updated and restarted by the launcher.")
    public Integer update;

}
