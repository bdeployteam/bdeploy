package io.bdeploy.interfaces.descriptor.application;

/**
 * Describes the application specific exit codes.
 */
public class ApplicationExitCodeDescriptor {

    /**
     * Exit code indicating that the application requests to update itself.
     * <p>
     * This exit code is only evaluated in case of a client application. The launcher
     * will install available updates and restart the application.
     * </p>
     */
    public Integer update;

}
