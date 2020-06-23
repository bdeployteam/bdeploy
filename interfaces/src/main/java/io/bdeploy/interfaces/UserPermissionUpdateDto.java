package io.bdeploy.interfaces;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Allows updating of user permissions on an instance group or a software repository
 */
public class UserPermissionUpdateDto {

    public final String user;
    public final Permission permission;

    @JsonCreator
    public UserPermissionUpdateDto(@JsonProperty("user") String user, @JsonProperty("permission") Permission permission) {
        this.user = user;
        this.permission = permission;
    }

}
