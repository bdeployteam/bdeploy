package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A DTO that contains a list of all UI Endpoints of a given instance.
 */
public class InstanceUiEndpointsDto {

    public String instanceId;

    /** The list of endpoints */
    public Collection<UiEndpointDto> endpoints = new ArrayList<>();

}
