package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A DTO that contains a list of all client applications of a given instance.
 */
public class InstanceClientAppsDto {

    public String instanceId;

    /** The list of client applications */
    public Collection<ClientApplicationDto> applications = new ArrayList<>();

}
