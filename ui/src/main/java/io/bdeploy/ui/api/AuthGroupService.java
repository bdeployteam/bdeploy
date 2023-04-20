package io.bdeploy.ui.api;

import java.util.Set;
import java.util.SortedSet;

import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;

public interface AuthGroupService {

    /**
     * @return all user groups known to the system with full user group information
     */
    public SortedSet<UserGroupInfo> getAll();

    /**
     * @return get user groups known to the system with full user group information (ignore unknown ids)
     */
    public SortedSet<UserGroupInfo> getUserGroups(Set<String> groupIds);

    /**
     * @param info the user group to create
     */
    public void createUserGroup(UserGroupInfo info);

    /**
     * @param info the updated user group data
     */
    public void updateUserGroup(UserGroupInfo info);

    /**
     * Updates the ScopedPermissions for a list of user groups on a single instance group or software repository.
     *
     * @param target the name of the instance group or software repository.
     * @param permissions list of user group with granted Permission.
     */
    public void updatePermissions(String target, UserGroupPermissionUpdateDto[] permissions);

    /**
     * Deletes the given user group
     *
     * @param group the id of the user group.
     */
    public void deleteUserGroup(String group);

    /**
     * @param info - user
     * @return cloned user with calculated mergedPermissions
     */
    public UserInfo getCloneWithMergedPermissions(UserInfo info);

}
