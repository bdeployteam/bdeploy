package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.nodes.NodeType;

/**
 * Describes which applications are running on a specific node
 */
public class InstanceNodeConfigurationDto {

    /** The name of the node */
    public final String nodeName;

    /** The actual configuration of the node. Not set when no configuration is assigned */
    public InstanceNodeConfiguration nodeConfiguration;

    @JsonCreator
    public InstanceNodeConfigurationDto(@JsonProperty("nodeName") String nodeName,
            @JsonProperty("nodeConfiguration") InstanceNodeConfiguration config) {
        // Explicitly set the name of client nodes for backwards compatibility
        this.nodeName = config != null && config.nodeType == NodeType.CLIENT ? InstanceManifest.CLIENT_NODE_NAME : nodeName;
        this.nodeConfiguration = config;
    }
}
