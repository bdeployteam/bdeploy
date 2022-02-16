package io.bdeploy.interfaces.descriptor.template;

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

    /** The name of the group */
    public String name;

    /** How processes are started */
    public ProcessControlGroupHandlingType startType = ProcessControlGroupHandlingType.PARALLEL;

    /** Whether to wait for process startup or not */
    public ProcessControlGroupWaitType startWait = ProcessControlGroupWaitType.WAIT;

    /** How processes are stopped */
    public ProcessControlGroupHandlingType stopType = ProcessControlGroupHandlingType.PARALLEL;

}
