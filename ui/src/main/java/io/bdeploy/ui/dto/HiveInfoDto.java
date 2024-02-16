package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Information about a single {@link BHive} hosted by the server.
 */
public class HiveInfoDto {

    public static enum HiveType {
        @JsonEnumDefaultValue
        PLAIN,
        INSTANCE_GROUP,
        SOFTWARE_REPO,
    }

    public String name;

    public boolean canPool;

    public boolean pooling;

    public String poolPath;

    public HiveType type = HiveType.PLAIN;

    public Permission minPermission;

}
