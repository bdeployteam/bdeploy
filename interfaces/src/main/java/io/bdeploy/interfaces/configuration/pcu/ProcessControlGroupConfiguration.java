package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a single Process Control Group. This is meta-information attached to an instance which defines the way processes are
 * started and stopped.
 * <p>
 * The defaults in this class match the classic BDeploy behavior so that if no configuration is present (yet), the behavior
 * stays the same.
 */
public class ProcessControlGroupConfiguration {

    public static final String DEFAULT_GROUP = "Default";

    public enum ProcessControlGroupHandlingType {
        SEQUENTIAL,
        PARALLEL
    }

    public enum ProcessControlGroupWaitType {
        /**
         * Immediately start the next process
         */
        CONTINUE,

        /**
         * Start the next process after the current process is in {@link ProcessState#RUNNING}
         */
        WAIT,

        /**
         * Start the next process after the current process is in {@link ProcessState#STOPPED}
         */
        WAIT_UNTIL_STOPPED
    }

    /** The name of the group */
    public String name = DEFAULT_GROUP;

    /** How processes are started */
    public ProcessControlGroupHandlingType startType = ProcessControlGroupHandlingType.SEQUENTIAL;

    /** When to continue with starting the next process */
    public ProcessControlGroupWaitType startWait = ProcessControlGroupWaitType.CONTINUE;

    /** How processes are stopped */
    public ProcessControlGroupHandlingType stopType = ProcessControlGroupHandlingType.SEQUENTIAL;

    /** Process IDs in the order to be started. Stop always uses reverse order of this */
    public List<String> processOrder = new ArrayList<>();

}
