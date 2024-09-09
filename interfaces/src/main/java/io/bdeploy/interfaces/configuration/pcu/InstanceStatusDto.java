package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds the deployment and runtime status of an entire instance.
 */
public class InstanceStatusDto {

    /** The unique ID of the instance */
    private final String instanceId;

    /** Node status informations. Key = NAME of the node */
    public final Map<String, InstanceNodeStatusDto> node2Applications = new ConcurrentHashMap<>();

    /**
     * Creates a new status DTO for the given instance.
     *
     * @param instanceId the instance ID
     */
    @JsonCreator
    public InstanceStatusDto(@JsonProperty("instanceId") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String toString() {
        return String.join("\n", log());
    }

    /**
     * Returns the instance ID.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns a human-readable string of the current instance status.
     */
    public List<String> log() {
        List<String> logs = new ArrayList<>();
        logs.add("Instance " + instanceId);
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            logs.add("\t" + entry.getKey());
            entry.getValue().log().forEach(l -> logs.add("\t\t" + l));
        }
        return logs;
    }

    /**
     * Adds the given node status to the list of children.
     *
     * @param minion
     *            the name of the minion
     * @param nodeStatus
     *            all running applications
     */
    public synchronized void add(String minion, InstanceNodeStatusDto nodeStatus) {
        node2Applications.put(minion, nodeStatus);
    }

    /**
     * Returns whether or not an application with the given UID is running or scheduled on any node.
     *
     * @param applicationId
     *            the application identifier
     * @return {@code true} if it is running and {@code false} otherwise
     */
    public boolean isAppRunningOrScheduled(String applicationId) {
        return getNodeWhereAppIsRunningOrScheduled(applicationId) != null;
    }

    /**
     * Returns the node where this application is running or scheduled on.
     *
     * @param applicationId
     *            the application identifier
     * @return the name of the node or {@code null} if not running
     */
    public String getNodeWhereAppIsRunningOrScheduled(String applicationId) {
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeStatusDto node = entry.getValue();
            if (node.isAppRunningOrScheduled(applicationId)) {
                return nodeName;
            }
        }
        return null;
    }

    /**
     * Returns the node where this application is running or scheduled on.
     *
     * @param applicationId
     *            the application identifier
     * @return the name of the node or {@code null} if not running
     */
    public String getNodeWhereAppIsRunning(String applicationId) {
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeStatusDto node = entry.getValue();
            if (node.isAppRunning(applicationId)) {
                return nodeName;
            }
        }
        return null;
    }

    /**
     * Returns a collection of nodes where at least one application is currently running.
     *
     * @return the list of nodes running at least one application
     */
    public Collection<String> getNodesWhereAppsAreRunningOrScheduled() {
        Set<String> nodes = new TreeSet<>();
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeStatusDto nodeDto = entry.getValue();
            if (!nodeDto.areAppsRunningOrScheduled()) {
                continue;
            }
            nodes.add(nodeName);
        }
        return nodes;
    }

    /**
     * Returns the node where the active version has this application deployed.
     *
     * @param applicationId
     *            the application identifier
     * @return the name of the node
     */
    public String getNodeWhereAppIsDeployed(String applicationId) {
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeStatusDto nodeDto = entry.getValue();
            if (nodeDto.isAppDeployed(applicationId)) {
                return nodeName;
            }
        }
        return null;
    }

    /**
     * Returns the status of the applications running or deployed on this instance.
     *
     * @return the application status. Key = ID of application
     */
    public Map<String, ProcessStatusDto> getAppStatus() {
        Map<String, ProcessStatusDto> statusMap = new HashMap<>();

        // Add all deployed applications of the active tag.
        for (InstanceNodeStatusDto nodeDto : node2Applications.values()) {
            if (nodeDto.activeTag != null) {
                ProcessListDto list = nodeDto.deployed.get(nodeDto.activeTag);
                statusMap.putAll(list.deployed);
            }
        }

        // Now put all running applications. Overwrites previous state.
        for (InstanceNodeStatusDto nodeDto : node2Applications.values()) {
            statusMap.putAll(nodeDto.runningOrScheduled);
        }
        return statusMap;
    }

    /**
     * Returns a collection of nodes where at least one application is deployed in the active version.
     *
     * @return a collection of nodes
     */
    public Collection<String> getNodesWithApps() {
        List<String> nodes = new ArrayList<>();
        for (Map.Entry<String, InstanceNodeStatusDto> entry : node2Applications.entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeStatusDto status = entry.getValue();
            if (!status.hasApps()) {
                continue;
            }
            nodes.add(nodeName);
        }
        return nodes;
    }

}
