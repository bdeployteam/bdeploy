package io.bdeploy.common.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes access rights to a given resource. Access rights can either be global or restricted (=scoped) to a given
 * resource. Global permissions take precedence over scoped permissions. Thus a user having global read permissions is allowed to
 * access any scoped resource with READ permissions. In contrast to that a user that has scoped READ permissions is not able to
 * access a resource requiring global read permissions.
 */
public final class ScopedCapability {

    /**
     * Available capabilities. Capabilities are inclusive meaning that higher permissions
     * include the lower ones. Thus a given resource must only define the lowest required
     * capability and not multiple ones.
     */
    public enum Capability {
        /**
         * The resource can be viewed but not modified.
         */
        READ(1),

        /**
         * The resource can be modified. Includes READ permissions.
         */
        WRITE(2),

        /**
         * New resources can be created and existing ones can be deleted.
         * Includes WRITE and READ permissions.
         */
        ADMIN(3);

        int level;

        /**
         * Creates a new capability with the given level. Higher level means higher permissions.
         */
        Capability(int level) {
            this.level = level;
        }
    }

    public final String scope;
    public final ScopedCapability.Capability capability;

    /**
     * Creates a new global capability.
     *
     * @param cap the capability
     */
    public ScopedCapability(ScopedCapability.Capability cap) {
        this(null, cap);
    }

    /**
     * Creates a new scoped capability allowing access to a particular resource.
     *
     * @param scope the scope of the resource
     * @param cap the capability
     */
    @JsonCreator
    public ScopedCapability(@JsonProperty("scope") String scope, @JsonProperty("capability") ScopedCapability.Capability cap) {
        this.scope = scope;
        this.capability = cap;
    }

    @Override
    public String toString() {
        if (scope == null) {
            return capability.name() + " (<<GLOBAL>>)";
        }
        return capability.name() + " (" + scope + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((capability == null) ? 0 : capability.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
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
        ScopedCapability other = (ScopedCapability) obj;
        if (capability != other.capability) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        return true;
    }

    /**
     * Returns whether or not this is a global scoped capability
     */
    public boolean isGlobal() {
        return scope == null;
    }

    /**
     * Checks whether or not this capability satisfies the given one. Capabilities are inclusive and ADMIN is the highest one.
     * The <tt>ADMIN</tt> capability implicitly grants <tt>WRITE</tt> and <tt>READ</tt> and the <tt>WRITE</tt> capability
     * implicitly grants <tt>READ</tt> capability.
     * <p>
     * <ul>
     * <li>If this token is a global one then the scoped of the other one is ignored.
     * Just the capability without the scoped is checked.</li>
     * <li>If this token is a scoped one and the other one is a global then always {@code false} will be returned.</li>
     * <li>If both are scoped capabilities then the scope must match. If so the capability will be compared.
     * </ul>
     *
     * @param other
     *            the capability to check
     * @return {@code true} if this capability satisfies the other one and {@code false} otherwise
     */
    public boolean satisfies(ScopedCapability other) {
        // If this is a global token the scope can be ignored
        // We just need to verify that we have the requested capability
        if (isGlobal()) {
            return compareCapability(other.capability) >= 0;
        }

        // If the other one is a global token access is not possible
        if (other.isGlobal()) {
            return false;
        }

        // When both are scoped they must be equal
        if (!scope.equals(other.scope)) {
            return false;
        }

        // Same scope, compare capabilities
        return compareCapability(other.capability) >= 0;
    }

    /**
     * Compares this capability with the other one.
     *
     * @param other the other capability
     * @return -1 if the this capability provides lower permissions than the other one, 0 if the capabilities are equal and 1 if
     *         this capability provides higher permissions
     */
    private int compareCapability(Capability other) {
        return Integer.compare(capability.level, other.level);
    }
}