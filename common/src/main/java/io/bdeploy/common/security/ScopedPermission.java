package io.bdeploy.common.security;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes access rights to a given resource. Access rights can either be global or restricted (=scoped) to a given resource.
 * Global permissions take precedence over scoped permissions. Thus a user having global read permissions is allowed to access any
 * scoped resource with READ permissions. In contrast to that a user that has scoped READ permissions is not able to access a
 * resource requiring global read permissions.
 */
public final class ScopedPermission {

    /** Permission for global administrators */
    public static final ScopedPermission GLOBAL_ADMIN = new ScopedPermission(ScopedPermission.Permission.ADMIN);

    /**
     * Available permissions. Permissions are inclusive meaning that higher permissions include the lower ones. Thus a given
     * resource must only define the lowest required permission and not multiple ones.
     */
    public enum Permission {

        /**
         * Permission for users allowed to *only* see/download clients.
         */
        CLIENT(1),

        /**
         * The resource can be viewed but not modified.
         */
        READ(2),

        /**
         * The resource can be modified. Includes READ permissions.
         */
        WRITE(3),

        /**
         * New resources can be created and existing ones can be deleted.
         * <p>
         * Includes WRITE and READ permissions.
         */
        ADMIN(4);

        private final int level;

        /**
         * Creates a new permission with the given level. Higher level means higher permissions.
         */
        Permission(int level) {
            this.level = level;
        }
    }

    public final String scope;
    @JsonAlias("capability") // renamed to permission a LOONG time ago, but is still in tokens out there.
    public final ScopedPermission.Permission permission;

    /**
     * Creates a new global permission.
     *
     * @param permission the permission
     */
    public ScopedPermission(ScopedPermission.Permission permission) {
        this(null, permission);
    }

    /**
     * Creates a new scoped permission allowing access to a particular resource.
     *
     * @param scope the scope of the resource
     * @param permission the permission
     */
    @JsonCreator
    public ScopedPermission(@JsonProperty("scope") String scope,
            @JsonProperty("permission") ScopedPermission.Permission permission) {
        this.scope = scope;
        this.permission = permission;
    }

    @Override
    public String toString() {
        if (scope == null) {
            return permission.name() + " (<<GLOBAL>>)";
        }
        return permission.name() + " (" + scope + ")";
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((permission == null) ? 0 : permission.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
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
        ScopedPermission other = (ScopedPermission) obj;
        if (permission != other.permission) {
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
     * Returns whether or not this is a global scoped permission
     */
    public boolean isGlobal() {
        return scope == null;
    }

    /**
     * Checks whether or not this permission satisfies the given one. Permissions are inclusive and ADMIN is the highest one. The
     * <tt>ADMIN</tt> permission implicitly grants <tt>WRITE</tt> and <tt>READ</tt> and the <tt>WRITE</tt> permission implicitly
     * grants <tt>READ</tt> permission.
     * <p>
     * <ul>
     * <li>If this token is a global one then the scoped of the other one is ignored. Just the permission without the scoped is
     * checked.
     * <li>If this token is a scoped one and the other one is a global then always {@code false} will be returned.
     * <li>If both are scoped permissions then the scope must match. If so the permission will be compared.
     * </ul>
     *
     * @param other the permission to check
     * @return {@code true} if this permission satisfies the other one and {@code false} otherwise
     */
    public boolean satisfies(ScopedPermission other) {
        // If this is a global token the scope can be ignored
        // We just need to verify that we have the requested permission
        if (isGlobal()) {
            return comparePermission(other.permission) >= 0;
        }

        // If the other one is a global token access is not possible
        if (other.isGlobal()) {
            return false;
        }

        // When both are scoped they must be equal
        if (!scope.equals(other.scope)) {
            return false;
        }

        // Same scope, compare permissions
        return comparePermission(other.permission) >= 0;
    }

    /**
     * Compares this permission with the other one.
     *
     * @param other the other permission
     * @return -1 if the this permission provides a lower permission level than the other one, 0 if the permissions are equal and
     *         1 if this permission provides a higher permission level
     */
    private int comparePermission(Permission other) {
        return Integer.compare(permission.level, other.level);
    }
}
