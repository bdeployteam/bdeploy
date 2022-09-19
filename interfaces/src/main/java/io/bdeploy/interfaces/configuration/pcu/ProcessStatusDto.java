package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * DTO provided by the PCU with the status of the process.
 */
public class ProcessStatusDto {

    /** The UID of the instance */
    @JsonAlias("instanceUid")
    public String instanceId;

    /** Tag of the instance */
    public String instanceTag;

    /** Unique id of the application */
    @JsonAlias("appUid")
    public String appId;

    /** Human readable name of the application */
    public String appName;

    /** Current process state */
    public ProcessState processState;

    /** The PID of the process */
    public long pid;

    /** The exit code if available */
    public int exitCode;

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
        logs.add("ProcessController [" + instanceId + " / " + instanceTag + " / " + appId + "]");
        logs.add("State: " + processState);
        return logs;
    }

}