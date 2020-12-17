package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Status information what is running on a given node. Note that there could be applications
 * running from multiple different tags.
 */
public class InstanceNodeStatusDto {

    /** All running applications. Key = UID of application */
    public Map<String, ProcessStatusDto> runningOrScheduled = new HashMap<>();

    /** All deployed applications. Key = TAG of instance node */
    public final Map<String, ProcessListDto> deployed = new TreeMap<>();

    /** The currently active tag */
    public String activeTag;

    @Override
    public String toString() {
        return String.join("\n", log());
    }

    /**
     * Returns the status of the given application.
     *
     * @param applicationId the application to lookup
     * @return the status or {@code null} if the application is not deployed
     */
    public ProcessStatusDto getStatus(String applicationId) {
        // Process might run in a version that is not the active one
        // Thus we need to query that first
        ProcessStatusDto status = runningOrScheduled.get(applicationId);
        if (status != null) {
            return status;
        }

        // Check active deployed version
        ProcessListDto processListDto = deployed.get(activeTag);
        return processListDto.get(applicationId);
    }

    /**
     * Adds the given process list DTO to this instance node.
     */
    public void add(String tag, ProcessListDto listDto) {
        for (ProcessStatusDto dto : listDto.runningOrScheduled.values()) {
            runningOrScheduled.put(dto.appUid, dto);
        }
        deployed.put(tag, listDto);
    }

    /**
     * Returns a human-readable string of the current node status.
     */
    public List<String> log() {
        List<String> logs = new ArrayList<>();
        logs.add("Active Tag: " + activeTag);
        logs.add("Running Apps: ");
        for (ProcessStatusDto process : runningOrScheduled.values()) {
            process.logStatusDetails().forEach(l -> logs.add("\t" + l));
        }
        if (runningOrScheduled.isEmpty()) {
            logs.add("\t(Nothing)");
        }
        return logs;
    }

    /**
     * Returns whether or not the given application is running or is scheduled to be started on this node.
     *
     * @param applicationId the application identifier
     * @return {@code true} if it is running and {@code false} otherwise
     */
    public boolean isAppRunningOrScheduled(String applicationId) {
        return runningOrScheduled.containsKey(applicationId);
    }

    /**
     * Returns whether or not the given application is running on this node.
     *
     * @param applicationId the application identifier
     * @return {@code true} if it is running and {@code false} otherwise
     */
    public boolean isAppRunning(String applicationId) {
        ProcessStatusDto processDto = runningOrScheduled.get(applicationId);
        if (processDto == null) {
            return false;
        }
        return processDto.processState.isRunning();
    }

    /**
     * Returns whether or not at least one running application is referring to the given version.
     *
     * @param tag the tag to compare
     * @return {@code true} if one application is running and {@code false} otherwise
     */
    public boolean areAppsRunningOrScheduledInVersion(String tag) {
        for (ProcessStatusDto status : runningOrScheduled.values()) {
            if (status.instanceTag.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether or not the given application is deployed in the given tag.
     *
     * @param tag
     *            the tag to compare
     * @param applicationId
     *            the application identifier
     * @return {@code true} if the application is available and {@code false} otherwise
     */
    public boolean isAppDeployedInVersion(String tag, String applicationId) {
        ProcessListDto processList = deployed.get(tag);
        if (processList == null) {
            return false;
        }
        return processList.isDeployed(applicationId);
    }

    /**
     * Returns whether or not the given application is deployed in the active tag.
     *
     * @param applicationId
     *            the application identifier
     * @return {@code true} if the application is available and {@code false} otherwise
     */
    public boolean isAppDeployed(String applicationId) {
        return isAppDeployedInVersion(activeTag, applicationId);
    }

    /**
     * Returns whether or not an application is currently running on this node.
     *
     * @return {@code true} if so and {@code false} otherwise
     */
    public boolean areAppsRunningOrScheduled() {
        return !runningOrScheduled.isEmpty();
    }

    /**
     * Returns whether or not at least a single application is deployed in the active version.
     *
     * @return {@code true} if so and {@code false} otherwise
     */
    public boolean hasApps() {
        ProcessListDto processList = deployed.get(activeTag);
        if (processList == null) {
            return false;
        }
        return !processList.deployed.isEmpty();
    }

}
