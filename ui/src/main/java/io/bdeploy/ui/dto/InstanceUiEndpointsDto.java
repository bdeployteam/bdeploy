package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.Collection;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;

/**
 * A DTO that contains a list of all UI Endpoints of a given instance.
 */
public class InstanceUiEndpointsDto {

    /** Information about the instance */
    public InstanceConfiguration instance;

    /** The list of endpoints */
    public Collection<UiEndpointDto> endpoints = new ArrayList<>();

}
