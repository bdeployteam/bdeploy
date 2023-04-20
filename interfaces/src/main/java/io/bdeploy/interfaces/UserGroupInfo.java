package io.bdeploy.interfaces;

import java.util.HashSet;
import java.util.Set;

import io.bdeploy.common.security.ScopedPermission;

/**
 * Information about a user group and permissions assigned to it.
 */
public class UserGroupInfo implements Comparable<UserGroupInfo> {

    public String id;
    public String name;
    public String description;
    public Set<ScopedPermission> permissions = new HashSet<>();
    public boolean inactive;

    @Override
    public int compareTo(UserGroupInfo o) {
        return name.compareTo(o.name);
    }

}
