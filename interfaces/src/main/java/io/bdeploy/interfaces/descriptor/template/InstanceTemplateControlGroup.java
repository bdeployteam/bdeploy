package io.bdeploy.interfaces.descriptor.template;

import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;

public class InstanceTemplateControlGroup {

    /** The name of the group */
    public String name;

    /** How processes are started */
    public ProcessControlGroupHandlingType startType = ProcessControlGroupHandlingType.SEQUENTIAL;

    /** Whether to wait for process startup or not */
    public ProcessControlGroupWaitType startWait = ProcessControlGroupWaitType.CONTINUE;

    /** How processes are stopped */
    public ProcessControlGroupHandlingType stopType = ProcessControlGroupHandlingType.SEQUENTIAL;

}
