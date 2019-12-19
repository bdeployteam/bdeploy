package io.bdeploy.launcher.cli;

/**
 * Exception thrown when an update is available but cannot be installed due to insufficient permissions.
 */
public class SoftwareUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Exception indicating that an update for the given application cannot be installed.
     */
    public SoftwareUpdateException(String appId, String installed, String available) {
        super(String.format("A required update for '%1$s' is available but cannot be installed. Installed=%2$s Available=%3$s",
                appId, installed, available));
    }

}
