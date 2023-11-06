package io.bdeploy.ui.dto;

import java.util.Set;

import io.bdeploy.common.security.ScopedPermission;

public class UserGroupBulkAssignPermissionDto {

    /** Permission that will be assigned to user groups */
    public ScopedPermission scopedPermission;

    /** IDs of user groups that permission will be assigned to */
    public Set<String> groupIds;

}
