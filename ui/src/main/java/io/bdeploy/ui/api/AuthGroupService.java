package io.bdeploy.ui.api;

import java.util.Set;
import java.util.SortedSet;

import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;

public interface AuthGroupService {

    /**
     * Lookup the given group's information.
     *
     * @param groupId the ID of the user group
     * @return all known information for the user group
     */
    public UserGroupInfo getUserGroup(String groupId);

    /**
     * Fetches all user groups known to the system with full user group information.
     *
     * @return a {@link SortedSet} of the retrieved data
     * @see #getUserGroups(Set)
     */
    public SortedSet<UserGroupInfo> getAll();

    /**
     * Fetches all specified user groups known to the system with full user group information. Silently ignores unknown IDs.
     *
     * @return a {@link SortedSet} of the retrieved data
     * @see #getAll()
     */
    public SortedSet<UserGroupInfo> getUserGroups(Set<String> groupIds);

    /**
     * Creates a new user group.
     *
     * @param info the user group to create
     */
    public void createUserGroup(UserGroupInfo info);

    /**
     * Updates the information of the given user group.
     *
     * @param info the updated user group data
     */
    public void updateUserGroup(UserGroupInfo info);

    /**
     * Updates the scoped permissions for an array of user groups within a single scope.
     *
     * @param scope the scope (name of the instance group or software repository)
     * @param permissions array of DTOs which contain data about a user group along with the {@link Permission} to assign
     */
    public void updatePermissions(String scope, UserGroupPermissionUpdateDto[] permissions);

    /**
     * Deletes the given user group.
     *
     * @param groupId the ID of the user group to delete
     */
    public void deleteUserGroup(String groupId);

    /**
     * @param info the {@link UserInfo} of the user to get the merged permissions for
     * @return cloned {@link UserInfo} with calculated mergedPermissions
     */
    public UserInfo getCloneWithMergedPermissions(UserInfo info);
}
