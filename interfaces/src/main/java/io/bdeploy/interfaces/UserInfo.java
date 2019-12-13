package io.bdeploy.interfaces;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ApiAccessToken.ScopedCapability;

/**
 * Information about a successfully authenticated user at the point in time where authentication happened.
 */
public class UserInfo {

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

    public long lastActiveLogin;

    public List<ScopedCapability> capabilities = new ArrayList<>();
    public List<String> recentlyUsedInstanceGroups = new ArrayList<>();

    @JsonCreator
    public UserInfo(@JsonProperty("name") String name) {
        this.name = name;
    }
}