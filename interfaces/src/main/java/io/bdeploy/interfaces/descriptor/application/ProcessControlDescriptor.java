package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Describes process control specific characteristics of an {@link ApplicationDescriptor application}.
 */
public class ProcessControlDescriptor {

    public enum ApplicationStartType {
        /**
         * Manual-only start using explicit process controls
         */
        @JsonEnumDefaultValue
        MANUAL,
        /**
         * Same as manual, but user must confirm start
         */
        MANUAL_CONFIRM,
        /**
         * Process started whenever the instance is started (automatically or manually).
         * <p>
         * Applications supporting {@link #INSTANCE} implicitly support {@link #MANUAL}.
         */
        INSTANCE
    }

    /**
     * The supported start type for the application.
     */
    public List<ApplicationStartType> supportedStartTypes = new ArrayList<>();

    /**
     * Keep-alive makes sure that the application is restarted once it exits.
     */
    public boolean supportsKeepAlive = false;

    /**
     * If {@link #supportsKeepAlive}, specifies the number of times the PCU will try to restart the process
     * if it keeps exiting within a certain timeout.
     */
    public long noOfRetries = 5;

    /**
     * Specifies the time in milliseconds that the PCU will wait for the application to exit after sending it the stop command.
     */
    public long gracePeriod = 30000;

    /**
     * Specifies if a process expects input on stdin.
     */
    public boolean attachStdin = false;

    /**
     * Optional startup probe which is queried to find out when a process completed startup.
     */
    public StartupProbeDescriptor startupProbe;

    /**
     * Optional lifeness probe which is queried to check on a process whether it is (still) alive.
     */
    public LifenessProbeDescriptor lifenessProbe;
}
