package io.bdeploy.ui.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;

/**
 * Allows updating of {@link InstanceConfiguration}
 */
public class InstanceConfigurationDto {

    /**
     * The instance node configuration. Not updated when <code>null</code>.
     */
    public InstanceConfiguration config;

    /**
     * The {@link InstanceNodeConfigurationDto}'s for each node to set. Not updated when <code>null</code>.
     */
    public List<InstanceNodeConfigurationDto> nodeDtos;

    @JsonCreator
    public InstanceConfigurationDto(@JsonProperty("config") InstanceConfiguration config,
            @JsonProperty("nodeDtos") List<InstanceNodeConfigurationDto> nodeDtos) {
        this.config = config;
        this.nodeDtos = nodeDtos;
    }

}
