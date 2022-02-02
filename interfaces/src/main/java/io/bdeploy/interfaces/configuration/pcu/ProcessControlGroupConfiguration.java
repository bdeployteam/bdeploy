package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a single Process Control Group. This is meta-information attached to an instance which defines the way processes are
 * started and stopped.
 * <p>
 * The defaults in this class match the classic BDeploy behaviour so that if no configuration is present (yet), the behaviour
 * stays the same.
 */
public class ProcessControlGroupConfiguration {

    public static final String DEFAULT_GROUP = "Default";

    public enum ProcessControlGroupHandlingType {
        SEQUENTIAL,
        PARALLEL
    }

    public enum ProcessControlGroupWaitType {
        WAIT,
        CONTINUE
    }

    /** The name of the group */
    public String name = DEFAULT_GROUP;

    /** How processes are started */
    public ProcessControlGroupHandlingType startType = ProcessControlGroupHandlingType.SEQUENTIAL;

    /** Whether to wait for process startup or not */
    public ProcessControlGroupWaitType startWait = ProcessControlGroupWaitType.CONTINUE;

    /** How processes are stopped */
    public ProcessControlGroupHandlingType stopType = ProcessControlGroupHandlingType.SEQUENTIAL;

    /** Process UUIDs in the order to be started. Stop always uses reverse order of this */
    public List<String> processOrder = new ArrayList<>();

}
