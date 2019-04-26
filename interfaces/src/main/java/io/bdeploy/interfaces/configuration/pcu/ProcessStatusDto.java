package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;

/**
 * DTO provided by the PCU with details about deployed application.
 */
public class ProcessStatusDto {

    /** Unique id of the application */
    public String appUid;

    /** The UID of the instance */
    public String instanceUid;

    /** Tag of the instance */
    public String instanceTag;

    /** Current process state */
    public ProcessState processState;

    /** Information about the main process. */
    public ProcessDetailDto processDetails;

    /** Time when the process stopped / crashed */
    public long stopTime = -1;

    /** Time when we are going to restart the application */
    public long recoverAt;

    /** Duration in seconds that we are waiting before re-launching */
    public long recoverDelay;

    /** Number of retry attempts */
    public long retryCount;

    /** Total number of retry attempts that are executed */
    public long maxRetryCount;

    @Override
    public String toString() {
        List<String> logs = new ArrayList<>();
        logs.addAll(logProcessDetails());
        logs.addAll(logDeploymentDetails());
        return Joiner.on("\n").join(logs);
    }

    /**
     * Returns a human readable string of the process status.
     */
    public List<String> logProcessDetails() {
        List<String> logs = new ArrayList<>();
        logs.add("Application: " + appUid);
        logs.add("Version: " + instanceTag);
        if (processDetails != null) {
            processDetails.log().forEach(l -> logs.add(l));
        }
        return logs;
    }

    /**
     * Returns a human readable string of the deployment details.
     */
    public List<String> logDeploymentDetails() {
        List<String> logs = new ArrayList<>();
        logs.add("Application: " + appUid);
        logs.add("\tVersion: " + instanceTag);
        return logs;
    }

}