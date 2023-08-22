package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

/**
 * Represents a deployed application configuration. Information is calculated
 * during configuration and deployment by combining the
 * {@link ApplicationDescriptor} with configuration information provided by the
 * user, and deployment information provided by the DCU.
 * <p>
 * This has a N:1 relation to a deployed {@link Manifest}. Each manifest is
 * deployed only once, but may be referenced (and launched) by multiple
 * {@link ProcessConfiguration}s. In fact, the
 * {@link ProcessConfiguration} does not even know what application
 * it is launching. This information is pre-calculated.
 */
public class ProcessConfiguration implements Comparable<ProcessConfiguration> {

    /**
     * Globally unique identifier of the process configuration.
     */
    @JsonAlias("uid")
    public String id;

    /**
     * @deprecated Compat with 4.x
     */
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    public String getUid() {
        return id;
    }

    /**
     * Name of the application, used for status reporting
     */
    public String name;

    /**
     * Configuration for process control specific properties.
     */
    public ProcessControlConfiguration processControl;

    /**
     * The start command for the application. This command is expected to stay alive
     * as long as the application is alive.
     */
    public final List<String> start = new ArrayList<>();

    /**
     * The stop command for the application. If this is not provided, the process
     * launched by the start command is killed (softly).
     * <p>
     * The stop command should only exit once the application has exited.
     */
    public final List<String> stop = new ArrayList<>();

    /**
     * List of configured endpoints which are relevant for process control.
     */
    public EndpointsConfiguration endpoints = new EndpointsConfiguration();

    /**
     * A map of environment variables to set when launching a process.
     */
    public Map<String, String> startEnv = new TreeMap<>();

    /**
     * A map of environment variables to set when launching the stop command.
     */
    public Map<String, String> stopEnv = new TreeMap<>();

    @Override
    public int compareTo(ProcessConfiguration o) {
        return id.compareTo(o.id);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ProcessConfiguration other = (ProcessConfiguration) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

}
