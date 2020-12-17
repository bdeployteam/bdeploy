package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.Collection;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;

/**
 * A DTO that contains a list of all client applications of a given instance.
 */
public class InstanceClientAppsDto {

    /** Information about the instance */
    public InstanceConfiguration instance;

    /** The list of client applications */
    public Collection<ClientApplicationDto> applications = new ArrayList<>();

}
