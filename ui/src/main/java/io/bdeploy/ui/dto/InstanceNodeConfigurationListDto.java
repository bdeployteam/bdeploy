package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

/**
 * Contains a list of all applications that are configured for a particular instance.
 */
public class InstanceNodeConfigurationListDto {

    /** Maps an application manifest key to its descriptor */
    public final Map<String, ApplicationDescriptor> applications = new HashMap<>();

    /** The applications running on the node */
    public final List<InstanceNodeConfigurationDto> nodeConfigDtos = new ArrayList<>();

}
