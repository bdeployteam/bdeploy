package io.bdeploy.interfaces.configuration.instance;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bundles all the information required to perform any update to an instance.
 */
public class InstanceUpdateDto {

    public InstanceConfigurationDto config;

    public List<FileStatusDto> files;

    @JsonCreator
    public InstanceUpdateDto(@JsonProperty("config") InstanceConfigurationDto config,
            @JsonProperty("files") List<FileStatusDto> files) {
        this.config = config;
        this.files = files;
    }

}
