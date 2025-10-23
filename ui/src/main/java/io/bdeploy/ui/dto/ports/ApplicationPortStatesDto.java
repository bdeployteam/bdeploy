package io.bdeploy.ui.dto.ports;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.nodes.NodeType;

public class ApplicationPortStatesDto {

    public final String appName;
    public final String appId;
    public final String configuredNode;
    public final NodeType nodeType;

    public final List<CompositePortStateDto> portStates = new ArrayList<>();

    public ApplicationPortStatesDto(String appName, String appId, String configuredNode, NodeType nodeType) {
        this.appName = appName;
        this.appId = appId;
        this.configuredNode = configuredNode;
        this.nodeType = nodeType;
    }

    public void addStates(List<CompositePortStateDto> portStatesDto) {
        portStates.addAll(portStatesDto);
    }

}
