package io.bdeploy.interfaces.descriptor.node;

import java.util.Objects;

import io.bdeploy.common.security.RemoteService;

/**
 * Describes the content of the {@literal Multi-Node connection} file.
 * <p>
 * The file only contains immutable data which is required to connect to and register with the master.
 */
public class MultiNodeConnectionDescriptor {

    /**
     * Remote master to connect to and register with
     */
    public RemoteService master;

    /**
     * ID of the multi-node to impersonate
     */
    public String name;

    @Override
    public int hashCode() {
        return Objects.hash(master, name);
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
        MultiNodeConnectionDescriptor other = (MultiNodeConnectionDescriptor) obj;
        return Objects.equals(master, other.master) && Objects.equals(name, other.name);
    }
}
