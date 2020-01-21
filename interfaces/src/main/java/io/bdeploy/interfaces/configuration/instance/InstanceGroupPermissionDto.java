package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ScopedCapability.Capability;

/**
 * Allows updating of {@link InstanceConfiguration}
 */
public class InstanceGroupPermissionDto {

    public final String user;
    public final Capability capability;

    @JsonCreator
    public InstanceGroupPermissionDto(@JsonProperty("user") String user, @JsonProperty("capability") Capability capability) {
        this.user = user;
        this.capability = capability;
    }

}
