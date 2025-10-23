package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.ws.rs.WebApplicationException;

/**
 * Holds the deployment and runtime status of an entire instance.
 */
public class InstanceStatusDto {

    /** The unique ID of the instance */
    private final String instanceId;

    /** Node status information. Key = NAME of the node */
    private final Map<String, InstanceNodeStatusDto> node2Applications = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> multiNodeToRuntimeNode = new ConcurrentHashMap<>();

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

    public synchronized void add(String multiNodeName, String runtimeNode, InstanceNodeStatusDto nodeStatus) {
        add(runtimeNode, nodeStatus);
        multiNodeToRuntimeNode.computeIfAbsent(multiNodeName, key -> new TreeSet<>()).add(runtimeNode);
    }

    public InstanceNodeStatusDto getNodeStatus(String serverNode) {
        return node2Applications.get(serverNode);
    }

    /**
     * Returns whether an application with the given UID is running or scheduled on any node.
     *
     * @param applicationId
     *            the application identifier
     * @return {@code true} if it is running and {@code false} otherwise
     */
    public boolean isAppRunningOrScheduled(String applicationId) {
        return !getNodesWhereAppIsRunningOrScheduled(applicationId).isEmpty();
    }

    /**
     * Returns the node where this application is running or scheduled on.
     *
     * @param applicationId the application identifier
     * @return the names of the node or an empty set if not running anywhere
     */
    public Set<String> getNodesWhereAppIsRunningOrScheduled(String applicationId) {
        return getNodesThat(instanceNodeStatusDto -> instanceNodeStatusDto.isAppRunningOrScheduled(applicationId));
    }

    /**
     * Returns a collection of nodes where at least one application is currently running.
     *
     * @return the set of nodes running at least one application
     */
    public Set<String> getNodesWhereAppsAreRunningOrScheduled() {
        return getNodesThat(InstanceNodeStatusDto::areAppsRunningOrScheduled);
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
     * Returns a map of statuses indexed by the app = UID.
     * The second level map is each process indexed by the name of the SERVER node this
     * process is running on.
     *
     */
    public Map<String, Map<String, ProcessStatusDto>> getAppsOnServerNodesStatus() {
        Map<String, Map<String, ProcessStatusDto>> statusMap = new HashMap<>();

        // Add all deployed applications of the active tag.
        node2Applications.forEach((nodeName, nodeDto) -> {
            if (nodeDto.activeTag != null) {
                ProcessListDto list = nodeDto.deployed.get(nodeDto.activeTag);
                list.deployed.forEach((appId, processStatusDto) ->
                        statusMap.computeIfAbsent(appId, k -> new HashMap<>()).put(nodeName, processStatusDto));
            }
        });

        // Now put all running applications. Overwrites previous state.
        node2Applications.forEach((nodeName, nodeDto) -> {
            nodeDto.runningOrScheduled.forEach((appId, processStatusDto) ->
                    statusMap.computeIfAbsent(appId, k -> new HashMap<>()).put(nodeName, processStatusDto));
        });
        return statusMap;
    }

    /**
     * Returns a collection of nodes where at least one application is deployed in the active version.
     * You must supply an explanation in case there are multiple entries that match.
     *
     * @return a collection of nodes
     */
    public Collection<String> getNodesWithApps() {
        return getNodesThat(InstanceNodeStatusDto::hasApps);
    }

    public String getSingleNodeThat(Predicate<InstanceNodeStatusDto> condition, String multipleMatchedExceptionDetail) {
        return getSingleNodeThat(condition, () -> multipleMatchedExceptionDetail);
    }

    public String getSingleNodeThat(Predicate<InstanceNodeStatusDto> condition, Supplier<String> multipleMatchedExceptionDetailSupplier) {
        Set<String> nodeNames = getNodesThat(condition);

        // none of the nodes matched the condition
        if (nodeNames.isEmpty()) {
            return null;
        }

        // wrong usage of the api; because no node was specified and yet this app is on multiple
        if (nodeNames.size() > 1) {
            throw new WebApplicationException(null == multipleMatchedExceptionDetailSupplier
                    ? "Found multiple nodes that match the required condition. "
                    : multipleMatchedExceptionDetailSupplier.get());
        }

        return nodeNames.iterator().next();
    }

    public boolean hasAtLeastOneNodeThat(Predicate<InstanceNodeStatusDto> condition) {
        return node2Applications.entrySet().stream().anyMatch(entry -> condition.test(entry.getValue()));
    }

    public Map<String, List<String>> getAppsGroupedByNodeOnWhichTheyRun(List<String> applicationIds) {
        Map<String, List<String>> groupedByNode = new TreeMap<>();

        for (var applicationId : applicationIds) {
            // Find node where the application is running
            Optional<String> node = node2Applications.entrySet().stream()
                    .filter(e -> e.getValue().hasApps() && e.getValue().getStatus(applicationId) != null
                            && e.getValue().getStatus(applicationId).processState != ProcessState.STOPPED)
                    .map(Map.Entry::getKey).findFirst();

            if (node.isEmpty()) {
                continue; // ignore - not deployed.
            }

            groupedByNode.computeIfAbsent(node.get(), n -> new ArrayList<>()).add(applicationId);
        }

        return groupedByNode;
    }

    public String identifyTopLevelNode(String nodeName) {
        return multiNodeToRuntimeNode.entrySet().stream()
                .filter(entry -> entry.getValue().contains(nodeName))
                .map(Map.Entry::getKey)
                .findAny().orElse(nodeName);
    }

    public boolean isMultiNode(String nodeName) {
        return multiNodeToRuntimeNode.containsKey(nodeName);
    }

    private Set<String> getNodesThat(Predicate<InstanceNodeStatusDto> condition) {
        return node2Applications.entrySet().stream().filter(entry -> condition.test(entry.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

}
