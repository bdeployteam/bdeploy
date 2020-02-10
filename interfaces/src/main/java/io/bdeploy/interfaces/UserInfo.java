package io.bdeploy.interfaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedCapability;

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

    public Set<ScopedCapability> capabilities = new HashSet<>();
    public List<String> recentlyUsedInstanceGroups = new ArrayList<>();

    @JsonCreator
    public UserInfo(@JsonProperty("name") String name) {
        this.name = normalizeName(name);
    }

    @Override
    public int compareTo(UserInfo o) {
        return name.compareTo(o.name);
    }

    /**
     * Returns a list of global capabilities assigned to this user
     *
     * @return the global capabilities
     */
    public Collection<ScopedCapability> getGlobalCapabilities() {
        return capabilities.stream().filter(ScopedCapability::isGlobal).collect(Collectors.toList());
    }

    /**
     * Removes leading as well as trailing spaces and converts the name into lower case.
     */
    public static String normalizeName(String name) {
        name = name.trim();
        name = name.toLowerCase();
        return name;
    }
}