package io.bdeploy.interfaces.configuration.pcu;

import io.bdeploy.interfaces.descriptor.application.LifenessProbeDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.descriptor.application.StartupProbeDescriptor;

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

    /**
     * Copy of the original startup probe descriptor.
     */
    public StartupProbeDescriptor startupProbe;

    /**
     * Copy of the original lifeness probe descriptor.
     */
    public LifenessProbeDescriptor lifenessProbe;

}
