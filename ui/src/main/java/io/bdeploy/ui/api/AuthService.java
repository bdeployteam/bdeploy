package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.interfaces.UserInfo;

public interface AuthService {

    /**
     * @param user the user to verify
     * @param pw the password to verify
     * @return the {@link UserInfo} for this user if authenticated, <code>null</code> otherwise.
     */
    public UserInfo authenticate(String user, String pw);

    /**
     * @param info the updated user information.
     */
    public void updateUserInfo(UserInfo info);

    /**
     * @param user the user to update
     * @param pw the new password to hash and store.
     */
    public void updateLocalPassword(String user, String pw);

    /**
     * Lookup the given user's information.
     *
     * @param name the name of the user
     * @return all known information for the user.
     */
    public UserInfo findUser(String name);

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

}
