package io.bdeploy.ui.api;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupPermissionDto;

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
     * Updates the ScopedPermissions for a list of users on a single InstanceGroup.
     *
     * @param group the name of the InstanceGroup.
     * @param permissions list of user with granted Permission.
     */
    public void updateInstanceGroupPermissions(String group, InstanceGroupPermissionDto[] permissions);

    /**
     * Removes the ScopedCapabilities for all users on a single InstanceGroup.
     *
     * @param group the name of the InstanceGroup.
     */
    public void removeInstanceGroupPermissions(String group);

    /**
     * @param user the user to create
     * @param pw the password to assign initially
     * @param permissions the initial permissions of the user.
     */
    public void createLocalUser(String user, String pw, Collection<ScopedPermission> permissions);

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
     * @return all users known to the system wit full user information
     */
    public SortedSet<UserInfo> getAll();

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
     * @param required the required permission
     * @return whether the user with the given name has the given permission.
     */
    public boolean isAuthorized(String user, ScopedPermission required);
}
