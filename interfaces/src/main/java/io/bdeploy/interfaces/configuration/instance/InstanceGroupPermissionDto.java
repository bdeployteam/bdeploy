package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Allows updating of {@link InstanceConfiguration}
 */
public class InstanceGroupPermissionDto {

    public final String user;
    public final Permission permission;

    @JsonCreator
    public InstanceGroupPermissionDto(@JsonProperty("user") String user, @JsonProperty("permission") Permission permission) {
        this.user = user;
        this.permission = permission;
    }

}
