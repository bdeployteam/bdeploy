package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO provided by the PCU with the status of the process.
 */
public class ProcessStatusDto {

    /** The UID of the instance */
    public String instanceUid;

    /** Tag of the instance */
    public String instanceTag;

    /** Unique id of the application */
    public String appUid;

    /** Human readable name of the application */
    public String appName;

    /** Current process state */
    public ProcessState processState;

    @Override
    public String toString() {
        List<String> logs = new ArrayList<>();
        logs.addAll(logStatusDetails());
        return String.join("\n", logs);
    }

    /**
     * Returns a human readable string of the current status.
     */
    public List<String> logStatusDetails() {
        List<String> logs = new ArrayList<>();
        logs.add("ProcessController [" + instanceUid + " / " + instanceTag + " / " + appUid + "]");
        logs.add("State: " + processState);
        return logs;
    }

}