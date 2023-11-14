package io.bdeploy.ui.dto;

import java.util.Set;

public class UserGroupBulkRemovePermissionDto {

    /** Scope of permission that will be removed from user groups */
    public String scope;

    /** IDs of user groups that permission will be removed from */
    public Set<String> groupIds;

}
