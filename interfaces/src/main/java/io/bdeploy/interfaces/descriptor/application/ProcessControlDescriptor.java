package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Describes process control specific characteristics of an {@link ApplicationDescriptor application}.
 */
public class ProcessControlDescriptor {

    // ################################################# only for server applications #################################################

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

    @JsonPropertyDescription("The supported start types for the application. 'INSTANCE' processes are automatically started when an instance is (auto-)started. 'MANUAL_CONFIRM' processes require an additional user confirmation before being started.")
    public List<ApplicationStartType> supportedStartTypes = new ArrayList<>();

    @JsonPropertyDescription("Whether the application should be restarted once it exits (regardless of exit code). Defaults to 'false'.")
    public boolean supportsKeepAlive = false;

    @JsonPropertyDescription("In case supportsKeepAlive is 'true', defines the amount of retries within a certain timeframe this application will be restarted. The 'failure' count resets after a few minutes of the application running without exiting. Defaults to '5'.")
    public long noOfRetries = 5;

    @JsonPropertyDescription("Specifies the time in milliseconds that the PCU will wait for the application to exit after sending it the stop command.")
    public long gracePeriod = 30000;

    @JsonPropertyDescription("Specifies if a process expects (and can/wants to handle) input on stdin.")
    public boolean attachStdin = false;

    @JsonPropertyDescription("Optional startup probe which is queried to find out when a process completed startup.")
    public StartupProbeDescriptor startupProbe;

    @JsonPropertyDescription("Optional liveness probe which is queried to check on a process whether it is (still) alive.")
    @JsonAlias("lifenessProbe")
    public LivenessProbeDescriptor livenessProbe;

    // ################################################# only for client applications #################################################

    @JsonPropertyDescription("Client applications only; Specifies a list of configuration sub-directories within the instance's configuration directory which should be made available on the client. Use with care. May expose security sensitive information to clients.")
    public String configDirs;

    @JsonPropertyDescription("Whether the application is allowed to automatically start upon system bootup. Defaults to 'false'.")
    public boolean supportsAutostart = false;

    @JsonPropertyDescription("The name of the script of the application in the PATH environment variable.")
    public String startScriptName;

    @JsonPropertyDescription("Whether the application should be allowed to start when server is offline. Defaults to 'false'.")
    public boolean offlineStartAllowed = false;

}
