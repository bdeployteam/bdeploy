package io.bdeploy.interfaces;

import java.util.HashSet;
import java.util.Set;

import io.bdeploy.common.security.ScopedPermission;
import jakarta.annotation.Generated;

/**
 * Information about a user group and permissions assigned to it.
 */
public class UserGroupInfo implements Comparable<UserGroupInfo> {

    // hard-coded id for special non-deletable group for all users
    public static final String ALL_USERS_GROUP_ID = "all-users-group";

    public String id;
    public String name;
    public String description;
    public Set<ScopedPermission> permissions = new HashSet<>();
    public boolean inactive;

    @Override
    public int compareTo(UserGroupInfo o) {
        if (ALL_USERS_GROUP_ID.equals(this.id)) {
            return -1;
        }
        if (ALL_USERS_GROUP_ID.equals(o.id)) {
            return 1;
        }
        return name.compareTo(o.name);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        UserGroupInfo other = (UserGroupInfo) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
