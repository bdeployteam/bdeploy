package io.bdeploy.ui.dto;

import java.util.Map;

import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;

public class InstanceProcessStatusDto {

    /**
     * Each process' state
     */
    public Map<String, ProcessStatusDto> processStates;

    /**
     * Mapping of application ID to node name;
     */
    public Map<String, String> processToNode;

}
