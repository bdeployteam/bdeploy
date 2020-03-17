package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;

import io.bdeploy.bhive.model.Manifest;
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
    public String uid;

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

    @Override
    public int compareTo(ProcessConfiguration o) {
        return uid.compareTo(o.uid);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uid == null) ? 0 : uid.hashCode());
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
        if (uid == null) {
            if (other.uid != null) {
                return false;
            }
        } else if (!uid.equals(other.uid)) {
            return false;
        }
        return true;
    }

}
