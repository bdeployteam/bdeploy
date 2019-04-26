package io.bdeploy.interfaces.configuration.pcu;

import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

/**
 * Counterpart of {@link ProcessControlDescriptor} defining actual values.
 */
public class ProcessControlConfiguration {

    /**
     * The configured start type of the application.
     */
    public ApplicationStartType startType;

    /**
     * Whether the application should be restarted when it exits.
     */
    public boolean keepAlive;

    /**
     * Number of times to try restarting the application if it exits.
     */
    public long noOfRetries;

    /**
     * Grace period for the application to stop when told to do so.
     */
    public long gracePeriod;

}
