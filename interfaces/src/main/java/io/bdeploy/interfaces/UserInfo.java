package io.bdeploy.interfaces;

import static io.bdeploy.interfaces.UserGroupInfo.ALL_USERS_GROUP_ID;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedPermission;
import jakarta.annotation.Generated;

/**
 * Information about a successfully authenticated user at the point in time where authentication happened.
 */
public class UserInfo implements Comparable<UserInfo> {

    public final String name;
    public String password;
    public String fullName;
    public String email;

    /** Marks a user a 'externally managed', meaning we don't have the password locally. */
    public boolean external;
    /** The type of external system. This is a string specific to each external system. */
    public String externalSystem;
    /** Allows the managing external system to attach some data for it's own purpose to the user */
    public String externalTag;

    /** Whether this user has been deactivated and should be denied access */
    public boolean inactive;

    public long lastActiveLogin;

    @JsonAlias("capabilities") // renamed to permissions
    public Set<ScopedPermission> permissions = new HashSet<>();

    /** User group ids the user belongs to */
    private Set<String> groups = new HashSet<>();

    /** Calculated set of user permissions and user's active groups permissions */
    public Set<ScopedPermission> mergedPermissions;

    @JsonCreator
    public UserInfo(@JsonProperty("name") String name) {
        this.name = normalizeName(name);
        groups.add(ALL_USERS_GROUP_ID);
    }

    public UserInfo(String name, boolean normalize) {
        this.name = normalize ? normalizeName(name) : name;
        groups.add(ALL_USERS_GROUP_ID);
    }

    @Override
    public int compareTo(UserInfo o) {
        return name.compareTo(o.name);
    }

    /**
     * Returns a list of global permissions assigned to this user
     *
     * @return the global permissions
     */
    public Collection<ScopedPermission> getGlobalPermissions() {
        return mergedPermissions.stream().filter(ScopedPermission::isGlobal).toList();
    }

    /** ALL_USERS_GROUP_ID is always a part of user groups */
    @JsonProperty("groups")
    public Set<String> getGroups() {
        return groups;
    }

    @JsonProperty("groups")
    public void setGroups(Set<String> groups) {
        this.groups = new HashSet<>();
        if (groups != null) {
            this.groups.addAll(groups);
        }
        this.groups.add(ALL_USERS_GROUP_ID);
    }

    /**
     * Removes leading as well as trailing spaces and converts the name into lower case.
     */
    public static String normalizeName(String name) {
        return name.trim().toLowerCase();
    }

    /**
     * @return a deep copy of this instance
     */
    public UserInfo deepCopy() {
        UserInfo copy = new UserInfo(this.name, false);
        copy.password = this.password;
        copy.fullName = this.fullName;
        copy.email = this.email;
        copy.external = this.external;
        copy.externalSystem = this.externalSystem;
        copy.externalTag = this.externalTag;
        copy.inactive = this.inactive;
        copy.lastActiveLogin = this.lastActiveLogin;
        copy.permissions = new HashSet<>(this.permissions);
        copy.groups = new HashSet<>(this.groups);
        if (this.mergedPermissions != null) {
            copy.mergedPermissions = new HashSet<>(this.mergedPermissions);
        }
        return copy;
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
        UserInfo other = (UserInfo) obj;
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
