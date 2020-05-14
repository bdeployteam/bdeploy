package io.bdeploy.interfaces.configuration.pcu;

import java.time.Duration;

import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

/**
 * Counterpart of {@link ProcessControlDescriptor} defining actual values.
 */
public class ProcessControlConfiguration {

    /**
     * Creates and returns a new configuration with reasonable defaults
     */
    public static ProcessControlConfiguration createDefault() {
        ProcessControlConfiguration config = new ProcessControlConfiguration();
        config.startType = ApplicationStartType.MANUAL;
        config.keepAlive = false;
        config.noOfRetries = 3;
        config.gracePeriod = Duration.ofSeconds(30).toMillis();
        config.attachStdin = false;
        return config;
    }

    /**
     * The configured start type of the application.
     */
    public ApplicationStartType startType;

    /**
     * Whether the application should be restarted when it exits.
     */
    public boolean keepAlive;

    /**
     * Number of times to try restarting the application if it terminates unexpectedly.
     * 0 means that the process control will never give up restarting the application.
     */
    public int noOfRetries;

    /**
     * Grace period in milliseconds for the application to stop when told to do so.
     */
    public long gracePeriod;

    /**
     * Specifies if a process expects input on stdin.
     */
    public boolean attachStdin;

}
