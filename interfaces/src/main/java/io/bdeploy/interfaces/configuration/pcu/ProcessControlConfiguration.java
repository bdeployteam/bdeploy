package io.bdeploy.interfaces.configuration.pcu;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.descriptor.application.LivenessProbeDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.descriptor.application.StartupProbeDescriptor;

/**
 * Counterpart of {@link ProcessControlDescriptor} defining actual values.
 */
public class ProcessControlConfiguration {

    public static final Pattern CONFIG_DIRS_SPLIT_PATTERN = Pattern.compile(",");

    // ################################################# only for server applications #################################################

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
     * Copy of the original liveness probe descriptor.
     */
    @JsonAlias("lifenessProbe")
    public LivenessProbeDescriptor livenessProbe;

    /**
     * Compatibility module when we act as REST client so we send the original name of the livenessProbe.
     * <p>
     * This enables new CENTRAL minions to still save configuration with older MANAGED minions (7.1.0 and older).
     * <p>
     * In the future we can remove this bridge and everybody will use the new format.
     *
     * @deprecated This is here for compatibility of central with managed servers which run older releases. This should be removed
     *             at the point where central servers are allowed to force managed servers to be newer than 7.1.0.
     */
    @Deprecated(since = "7.1.0", forRemoval = true)
    @JsonProperty
    public LivenessProbeDescriptor getLifenessProbe() {
        return livenessProbe;
    }

    // ################################################# only for client applications #################################################

    /**
     * A (comma separated) list of "allowed" paths in the config tree. Currently only used for client applications.
     * <p>
     * Only listed directories will be provisioned to the client.
     */
    public String configDirs;

    /**
     * Whether the application should autostart on system boot
     */
    public boolean autostart;
}
