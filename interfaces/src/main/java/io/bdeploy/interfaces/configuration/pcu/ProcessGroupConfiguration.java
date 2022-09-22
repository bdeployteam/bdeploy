package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

/**
 * Describes a named deployment. This is provided by the DCU, by writing it to a
 * file in the deployment directory.
 */
public class ProcessGroupConfiguration implements Comparable<ProcessGroupConfiguration> {

    /**
     * The name of the instance. E.g. "Test System", "Productive System", etc.
     */
    public String name;

    /**
     * The ID of the instance..
     */
    @JsonAlias("uuid")
    public String id;

    // Compat with 4.x
    @Deprecated(forRemoval = true)
    @JsonProperty("uuid")
    public String getUuid() {
        return id;
    };

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
        ProcessGroupConfiguration other = (ProcessGroupConfiguration) obj;
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
