package io.bdeploy.ui.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ApiAccessToken.ScopedCapability;

public interface AuthService {

    /**
     * @param user the user to verify
     * @param pw the password to verify
     * @return the {@link UserInfo} for this user if authenticated, <code>null</code> otherwise.
     */
    public UserInfo authenticate(String user, String pw);

    /**
     * @param user the user to get recently used for
     * @return the list of recently used items (instance groups). The latest one added is the last in the list.
     */
    public List<String> getRecentlyUsedInstanceGroups(String user);

    /**
     * @param user the user to add a recently used item to
     * @param group the instance group to add.
     */
    public void addRecentlyUsedInstanceGroup(String user, String group);

    /**
     * Information about a successfully authenticated user at the point in time where authentication happened.
     */
    public class UserInfo {

        public final String name;
        public String password;

        public List<ScopedCapability> capabilities = new ArrayList<>();
        public List<String> recentlyUsedInstanceGroups = new ArrayList<>();

        @JsonCreator
        public UserInfo(@JsonProperty("name") String name) {
            this.name = name;
        }
    }

}
