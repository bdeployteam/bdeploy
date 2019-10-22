package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.ui.api.MinionMode;

/**
 * Basic information about the running server.
 */
public class BackendInfoDto {

    public String version;
    public MinionMode mode;

    @JsonCreator
    public BackendInfoDto(@JsonProperty("version") String version, @JsonProperty("mode") MinionMode mode) {
        this.version = version;
        this.mode = mode;
    }

}
