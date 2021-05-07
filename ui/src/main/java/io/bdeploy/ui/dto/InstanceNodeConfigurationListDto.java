package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;

/**
 * Contains a list of all applications that are configured for a particular instance.
 */
public class InstanceNodeConfigurationListDto {

    /** Maps an application manifest key to its descriptor */
    public final List<ApplicationDto> applications = new ArrayList<>();

    /** The applications running on the node */
    public final List<InstanceNodeConfigurationDto> nodeConfigDtos = new ArrayList<>();

}
