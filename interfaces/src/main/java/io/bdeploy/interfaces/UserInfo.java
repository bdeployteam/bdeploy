package io.bdeploy.interfaces;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedPermission;

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

    @JsonCreator
    public UserInfo(@JsonProperty("name") String name) {
        this.name = normalizeName(name);
    }

    public UserInfo(String name, boolean normalize) {
        this.name = normalize ? normalizeName(name) : name;
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
        return permissions.stream().filter(ScopedPermission::isGlobal).collect(Collectors.toList());
    }

    /**
     * Removes leading as well as trailing spaces and converts the name into lower case.
     */
    public static String normalizeName(String name) {
        name = name.trim();
        name = name.toLowerCase();
        return name;
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