package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.NodeStatus;

/**
 * Describes which applications are running on a specific node
 */
public class InstanceNodeConfigurationDto {

    /** The name of the node */
    public final String nodeName;

    /** The status of the node as determined by contacting the master */
    public final NodeStatus status;

    /** A hint text to display along with the node status */
    public final String statusHint;

    /** The actual configuration of the node. Not set when no configuration is assigned */
    public InstanceNodeConfiguration nodeConfiguration;

    /** The list of configurations belonging to another instance */
    public final List<InstanceNodeConfiguration> foreignNodeConfigurations = new ArrayList<>();

    @JsonCreator
    public InstanceNodeConfigurationDto(@JsonProperty("nodeName") String nodeName, @JsonProperty("status") NodeStatus status,
            @JsonProperty("statusHint") String hint) {
        this.nodeName = nodeName;
        this.status = status;
        this.statusHint = hint;
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
