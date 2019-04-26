package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

/**
 * Describes a named deployment. This is provided by the DCU, by writing it to a
 * file in the deployment directory.
 */
public class ProcessGroupConfiguration implements Comparable<ProcessGroupConfiguration> {

    /**
     * The name of the deployment. E.g. "Test System", "Productive System", etc.
     */
    public String name;

    /**
     * The UUID of the deployment.
     */
    public String uuid;

    /**
     * Whether to automatically start this deployment (any {@link ProcessConfiguration}s using start type
     * {@link ApplicationStartType#INSTANCE}) when starting up.
     */
    public boolean autoStart;

    /**
     * All {@link ProcessConfiguration}s belonging to this deployment.
     */
    public final List<ProcessConfiguration> applications = new ArrayList<>();

    @Override
    public int compareTo(ProcessGroupConfiguration o) {
        return uuid.compareTo(o.uuid);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

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
        ProcessGroupConfiguration other = (ProcessGroupConfiguration) obj;
        if (uuid == null) {
            if (other.uuid != null) {
                return false;
            }
        } else if (!uuid.equals(other.uuid)) {
            return false;
        }
        return true;
    }

}
