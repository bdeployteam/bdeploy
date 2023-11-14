package io.bdeploy.ui.dto;

import java.util.Set;

public class UserBulkRemovePermissionDto {

    /** Scope of permission that will be removed from users */
    public String scope;

    /** Names of users that permission will be removed from */
    public Set<String> userNames;

}
