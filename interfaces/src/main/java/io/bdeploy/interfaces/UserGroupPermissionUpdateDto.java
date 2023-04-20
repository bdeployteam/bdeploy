package io.bdeploy.interfaces;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Allows updating of user group permissions on an instance group or a software repository
 */
public class UserGroupPermissionUpdateDto {

    public final String group;
    public final Permission permission;

    @JsonCreator
    public UserGroupPermissionUpdateDto(@JsonProperty("group") String group, @JsonProperty("permission") Permission permission) {
        this.group = group;
        this.permission = permission;
    }

}
