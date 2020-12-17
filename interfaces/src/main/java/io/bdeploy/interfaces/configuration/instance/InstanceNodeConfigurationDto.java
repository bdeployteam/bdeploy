package io.bdeploy.interfaces.configuration.instance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Groups the given node descriptions by the target node name
     */
    public static Map<String, InstanceNodeConfigurationDto> groupByNode(Collection<InstanceNodeConfigurationDto> values) {
        Map<String, InstanceNodeConfigurationDto> map = new HashMap<>();
        for (InstanceNodeConfigurationDto value : values) {
            map.put(value.nodeName, value);
        }
        return map;
    }

}
