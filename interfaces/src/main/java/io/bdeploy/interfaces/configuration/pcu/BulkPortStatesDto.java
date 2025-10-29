package io.bdeploy.interfaces.configuration.pcu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BulkPortStatesDto {

    // first key = configured node
    // second key = the server node from which the data has been gathered
    // PS the first key == the second key and there is only one entry if the configured node is a server node
    private final Map<String, Map<String, Map<Integer, Boolean>>> node2Ports = new HashMap<>();

    synchronized public void saveNodeState(String configuredNode, String serverNode, Integer port, Boolean isUsed) {
        node2Ports.computeIfAbsent(configuredNode, k -> new HashMap<>()).computeIfAbsent(serverNode, k -> new HashMap<>())
                .computeIfAbsent(port, k -> isUsed);
    }

    public Map<String, Map<Integer, Boolean>> getNodePortsState(String nodeName) {
        return node2Ports.containsKey(nodeName) ? Collections.unmodifiableMap(node2Ports.get(nodeName)) : Collections.emptyMap();
    }

}
