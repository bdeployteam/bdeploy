package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;

/**
 * Defines a Process Control Group in an Instance Template with default settings.
 * <p>
 * Note that the default settings are very different from the "no group defined" default control group. This is due to the fact
 * that the default needs to be compatible with previous BDeploy behavior, and this one needs to make sense the most, since this
 * is only used when explicitly specified.
 */
public class InstanceTemplateControlGroup {

    @JsonPropertyDescription("The name of the group")
    public String name;

    @JsonPropertyDescription("Defines how processes are started within this group if multiple processes are started.")
    public ProcessControlGroupHandlingType startType = ProcessControlGroupHandlingType.PARALLEL;

    @JsonPropertyDescription("When to continue with starting the next process")
    public ProcessControlGroupWaitType startWait = ProcessControlGroupWaitType.WAIT;

    @JsonPropertyDescription("Defines how processes are stopped within this group if multiple processes are stopped.")
    public ProcessControlGroupHandlingType stopType = ProcessControlGroupHandlingType.PARALLEL;

}
