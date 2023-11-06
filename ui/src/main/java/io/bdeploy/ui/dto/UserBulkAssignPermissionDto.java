package io.bdeploy.ui.dto;

import java.util.Set;

import io.bdeploy.common.security.ScopedPermission;

public class UserBulkAssignPermissionDto {

    /** Permission that will be assigned to users */
    public ScopedPermission scopedPermission;

    /** Names of users that permission will be assigned to */
    public Set<String> userNames;

}
