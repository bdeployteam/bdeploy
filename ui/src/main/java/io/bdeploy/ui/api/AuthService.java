package io.bdeploy.ui.api;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.common.security.ScopedCapability;
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
     * @param user the user to create
     * @param pw the password to assign initially
     * @param capabilities the initial capabilities of the user.
     */
    public void createLocalUser(String user, String pw, Collection<ScopedCapability> capabilities);

    /**
     * @param user the user to update
     * @param pw the new password to hash and store.
     */
    public void updateLocalPassword(String user, String pw);

    /**
     * Deletes the given user, regardless of whether it is local or externally managed.
     *
     * @param name the name of the user.
     */
    public void deleteUser(String name);

    /**
     * Lookup the given user's information.
     *
     * @param name the name of the user
     * @return all known information for the user.
     */
    public UserInfo getUser(String name);

    /**
     * @return all users known to the system without loading the full user information
     */
    public SortedSet<String> getAllNames();

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
     * @param user the user's name
     * @param required the required capability
     * @return whether the user with the given name has the given capability.
     */
    public boolean isAuthorized(String user, ScopedCapability required);
}
