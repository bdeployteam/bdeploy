package io.bdeploy.interfaces.configuration.pcu;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * DTO provided by the PCU with details about deployed application.
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

    /** True if the stdin stream is available/open for writing */
    public boolean hasStdin;

    @Override
    public String toString() {
        List<String> logs = new ArrayList<>();
        logs.addAll(logStatusDetails());
        logProcessDetails().forEach(l -> logs.add("\t" + l));
        return String.join("\n", logs);
    }

    /**
     * Returns a human readable string of the current status.
     */
    public List<String> logStatusDetails() {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        List<String> logs = new ArrayList<>();
        logs.add("ProcessController [" + instanceUid + " / " + instanceTag + " / " + appUid + "]");
        logs.add("State: " + processState);
        if (processState.isRunning()) {
            logs.add("STDIN: " + (hasStdin ? "available" : "not available"));
        }
        if (!processState.isRunning() && stopTime > 0) {
            logs.add("Stopped At: " + format.format(new Date(stopTime)));
        }
        if (recoverAt > 0) {
            logs.add("Restart At: " + format.format(new Date(recoverAt)));
        }
        if (retryCount > 0) {
            logs.add("Recoverd: " + retryCount + "/" + maxRetryCount);
        }
        return logs;
    }

    /**
     * Returns a human readable string of the launched processes.
     */
    public List<String> logProcessDetails() {
        List<String> logs = new ArrayList<>();
        if (processDetails != null) {
            processDetails.log().forEach(logs::add);
        }
        return logs;
    }

}