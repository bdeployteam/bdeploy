package io.bdeploy.ui.dto;

import java.util.Map;
import java.util.Set;

import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;

public class MappedInstanceProcessStatusDto {

    /**
     * Each process' state
     */
    public Map<String, Map<String,ProcessStatusDto>> processStates;

    /**
     * Mapping of application ID to node name;
     */
    public Map<String, String> processToNode;

    /**
     * Mapping of application ID to node name;
     */
    public Map<String, Set<String>> multiNodeToRuntimeNode;
}
