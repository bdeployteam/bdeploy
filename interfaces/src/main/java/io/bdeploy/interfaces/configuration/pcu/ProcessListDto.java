package io.bdeploy.interfaces.configuration.pcu;

import java.util.Map;
import java.util.TreeMap;

/**
 * Contains information about deployed and running applications of a single version.
 */
public class ProcessListDto {

    /** All running applications. Key = UID of applications */
    public final Map<String, ProcessStatusDto> runningOrScheduled = new TreeMap<>();

    /** All deployed applications. Key = UID of applications */
    public final Map<String, ProcessStatusDto> deployed = new TreeMap<>();

    /**
     * Adds the given process status.
     */
    public void add(ProcessStatusDto data) {
        if (data.processState.isRunningOrScheduled()) {
            runningOrScheduled.put(data.appUid, data);
        }
        deployed.put(data.appUid, data);
    }

    /**
     * Returns whether or not the given application is known.
     *
     * @param applicationId the application to check
     * @return {@code true} if the application exists and {@code false} otherwise
     */
    public boolean isDeployed(String applicationId) {
        return deployed.containsKey(applicationId);
    }

    /**
     * Returns the status of the given application
     *
     * @param applicationId
     *            the application identifier
     * @return the application status
     */
    public ProcessStatusDto get(String applicationId) {
        return deployed.get(applicationId);
    }

}
