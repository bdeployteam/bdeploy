package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes which applications are running on a specific node
 */
public class InstanceNodeConfigurationDto {

    /** The name of the node */
    public final String nodeName;

    /** The actual configuration of the node. Not set when no configuration is assigned */
    public InstanceNodeConfiguration nodeConfiguration;

    @JsonCreator
    public InstanceNodeConfigurationDto(@JsonProperty("nodeName") String nodeName) {
        this.nodeName = nodeName;
    }

}
