package io.bdeploy.ui.dto.ports;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.bdeploy.interfaces.nodes.NodeType;

public class InstancePortStatesDto {

    public final List<ApplicationPortStatesDto> appStates = new ArrayList<>();

    public void addApplicationPortState(String appName, String appId, String nodeName, NodeType nodeType,
            List<CompositePortStateDto> portStates) {
        ApplicationPortStatesDto appState = new ApplicationPortStatesDto(appName, appId, nodeName, nodeType);
        appState.addStates(portStates);
        appStates.add(appState);
    }

    public Map<String, List<Integer>> getPortsMappedByConfiguredNode() {
        return appStates.stream()
                .flatMap(appState -> appState.portStates.stream().map(portState -> Map.entry(appState.appId, portState.port)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

}
