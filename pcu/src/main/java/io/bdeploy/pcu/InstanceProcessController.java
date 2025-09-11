package io.bdeploy.pcu;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import com.google.common.collect.Sets;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryManager;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.Resolvers;

/**
 * Manages all processes of a given instance and knows which processes from which version are running. The controller will
 * instantiate a new {@linkplain ProcessController process controller} for each instance.
 */
public class InstanceProcessController {

    /** States that indicate that the process is running */
    private static final Set<ProcessState> SET_RUNNING = Sets
            .immutableEnumSet(Arrays.stream(ProcessState.values()).filter(ProcessState::isRunning).toList());

    /** States that indicate that the process is running or scheduled, sync with */
    private static final Set<ProcessState> SET_RUNNING_SCHEDULED = Sets
            .immutableEnumSet(Arrays.stream(ProcessState.values()).filter(ProcessState::isRunningOrScheduled).toList());

    private final MdcLogger logger = new MdcLogger(InstanceProcessController.class);

    /** Maps the tag of an instance to the list of processes */
    private final Map<String, ProcessList> processMap = new ConcurrentHashMap<>();

    /** The currently active tag */
    private String activeTag;

    /** The instance ID */
    private final String instanceId;

    /**
     * Create a new instance controller.
     */
    public InstanceProcessController(String instanceId) {
        this.instanceId = instanceId;
        this.logger.setMdcValue(instanceId);
    }

    /**
     * Creates new process controllers for all applications defined in the process group and adds a listener for runtime events.
     *
     * @param pathProvider provides access to the special folders
     * @param resolver the resolver for variables
     * @param inm the instance node manifest used for additional resolver lookups.
     * @param tag version of the configuration
     * @param groupConfig the process configuration
     * @param runtimeHistory optional {@link MinionRuntimeHistoryManager} to write to.
     */
    public void createProcessControllers(DeploymentPathProvider pathProvider, VariableResolver resolver, InstanceNodeManifest inm,
            String tag, ProcessGroupConfiguration groupConfig, MinionRuntimeHistoryManager runtimeHistory) {
        ProcessList processList = new ProcessList(tag, groupConfig);
        processMap.put(tag, processList);

        // Fetch all process control groups which are available.
        processList.setControlGroupsFromConfig(inm);

        // Add a new controller for each application
        for (ProcessConfiguration config : groupConfig.applications) {
            Path processDir = pathProvider.get(SpecialDirectory.RUNTIME).resolve(config.id);
            ProcessController controller = new ProcessController(groupConfig.id, tag, config, processDir);
            controller.setVariableResolver(inm == null ? resolver : Resolvers.forProcess(resolver, inm.getConfiguration(), config));

            if (runtimeHistory != null) {
                controller.addStatusListener(event -> {
                    // Do not record planned events and transitions.
                    if (event.newState == ProcessState.RUNNING_STOP_PLANNED) {
                        return;
                    }
                    // Do not record internal state changes
                    if (event.newState == ProcessState.RUNNING && event.oldState == ProcessState.RUNNING_UNSTABLE) {
                        return;
                    }
                    ProcessStatusDto status = controller.getStatus();
                    runtimeHistory.recordEvent(status.pid, status.exitCode, status.processState, config.name, event.user);
                });
            }

            processList.add(controller);
            logger.log(l -> l.debug("Creating new process controller."), tag, config.id);
        }
    }

    /**
     * Sets the active version of this instance. The active version is used when starting a single application
     * or when starting all applications of this instance.
     */
    public void setActiveTag(String activeTag) {
        logger.log(l -> l.info("Setting active tag to {}.", activeTag));
        this.activeTag = activeTag;
    }

    /**
     * Checks which applications of this instance are still running.
     */
    public void recover() {
        Map<String, Integer> tag2Running = new TreeMap<>();
        logger.log(l -> l.info("Checking which applications are alive."));
        for (ProcessList list : processMap.values()) {
            int running = list.recover();
            if (running == 0) {
                continue;
            }
            tag2Running.put(list.getInstanceTag(), running);
        }

        // Print out a summary. Indicate if applications across different versions are running
        if (tag2Running.isEmpty()) {
            logger.log(l -> l.info("No applications are running."));
        } else if (tag2Running.size() == 1) {
            Integer counter = tag2Running.values().iterator().next();
            logger.log(l -> l.info("{} application(s) are running.", counter));
        } else {
            Integer counter = tag2Running.values().stream().mapToInt(Integer::intValue).sum();
            logger.log(l -> l.info("{} application(s) from multiple different versions are running.", counter));
        }
    }

    /**
     * Starts all applications of the currently active tag that have the auto-start flag set.
     */
    public void autoStart() {
        if (activeTag == null) {
            logger.log(l -> l.info("Autostart not possible. No active tag has been set."));
            return;
        }
        // Check auto-start flag of instance
        ProcessList list = processMap.get(activeTag);
        if (!list.processConfig.autoStart) {
            logger.log(l -> l.info("Autostart not configured. Applications remain stopped."), activeTag);
            return;
        }
        logger.log(l -> l.info("Auto-Starting applications."), activeTag);

        // Proceed with normal startup of all processes
        startAll(null);
    }

    /**
     * @return All running processes in the instance *regardless* of the instance tag they have been started from.
     */
    private Map<String, ProcessController> getAllRunningAndScheduled() {
        Map<String, ProcessController> running = new TreeMap<>();
        for (ProcessList list : processMap.values()) {
            running.putAll(list.getWithState(SET_RUNNING_SCHEDULED));
        }
        return running;
    }

    /**
     * @param operation an operation which requires both the list of running processes as well as the controllers for the *active*
     *            instance.
     */
    private void performStartOperation(BiConsumer<ProcessList, Map<String, ProcessController>> operation) {
        if (activeTag == null) {
            throw new PcuRuntimeException("No active tag has been set");
        }

        // Ensure that something is deployed
        ProcessList list = processMap.get(activeTag);
        if (list == null) {
            throw new PcuRuntimeException("Activated version '" + activeTag + "' is not deployed");
        }

        // Compute runtime state across all versions
        Map<String, ProcessController> running = getAllRunningAndScheduled();

        // Let the desired strategy start all processes
        operation.accept(list, running);
    }

    /**
     * Starts all applications of the currently active tag.
     *
     * @param user the user who triggered the stop - null for default user
     */
    public void startAll(String user) {
        performStartOperation((list, running) -> {
            // Start all missing applications
            logger.log(l -> l.info("Starting all applications."), activeTag);

            BulkProcessController strategy = new BulkProcessController(instanceId, activeTag, list);
            strategy.startAll(user, running,
                    list.controllers.entrySet().stream()
                            .filter(e -> e.getValue().getDescriptor().processControl.startType == ApplicationStartType.INSTANCE)
                            .map(Entry::getKey).toList());
        });
    }

    /**
     * Stops all running processes of this instance. Processes are stopped in the order that is defined by the given list. If a
     * process is running but it is not contained in this list then it is stopped before all other processes. If the order is
     * undefined for multiple processes then the order is not guaranteed.
     *
     * @param user the user who triggered the stop - null for default user
     */
    public void stopAll(String user) {
        // Determine all running versions across all tags
        Map<String, ProcessController> running = getAllRunningAndScheduled();

        logger.log(l -> l.info("Stopping all running applications."));

        BulkProcessController strategy = new BulkProcessController(instanceId, activeTag, processMap.get(activeTag));
        strategy.stopAll(user, running, running.keySet());
    }

    /**
     * Starts the latest activated version of the given application.
     *
     * @param applicationIds
     *            the application identifiers
     * @param user the user who triggered the start
     */
    public void start(List<String> applicationIds, String user) {
        performStartOperation((list, running) -> {
            // Start all missing applications
            logger.log(l -> l.info("Starting applications: {}", applicationIds), activeTag);

            // Let the desired strategy start all processes
            BulkProcessController strategy = new BulkProcessController(instanceId, activeTag, list);
            strategy.startAll(user, running, applicationIds);
        });
    }

    /**
     * Stops the given application.
     *
     * @param applicationIds
     *            the application identifiers
     * @param user the user who triggered the stop
     */
    public void stop(List<String> applicationIds, String user) {
        // Determine all running versions across all tags
        Map<String, ProcessController> running = getAllRunningAndScheduled();

        logger.log(l -> l.info("Stopping applications: {}", applicationIds));

        BulkProcessController strategy = new BulkProcessController(instanceId, activeTag, processMap.get(activeTag));
        strategy.stopAll(user, running, applicationIds);
    }

    /**
     * Returns runtime details about this instance node.
     *
     * @return information about running applications.
     */
    public InstanceNodeStatusDto getStatus() {
        InstanceNodeStatusDto status = new InstanceNodeStatusDto();
        status.activeTag = activeTag;
        for (Map.Entry<String, ProcessList> entry : processMap.entrySet()) {
            status.add(entry.getKey(), entry.getValue().getStatus());
        }
        return status;
    }

    /**
     * Returns the detailed process status for the given application.
     *
     * @param appId
     *            the application identifier
     * @return the process details
     */
    public ProcessDetailDto getDetails(String appId) {
        // Get details if it is running or scheduled
        ProcessController processController = findProcessController(appId, SET_RUNNING_SCHEDULED);
        if (processController != null) {
            return processController.getDetails();
        }

        // Get details of the activated version
        ProcessList processList = processMap.get(activeTag);
        return processList.get(appId).getDetails();
    }

    /**
     * TESTING only: Detaches all running applications so that they cannot be controlled any more.
     */
    public void detach() {
        for (ProcessList list : processMap.values()) {
            Map<String, ProcessController> running = list.getWithState(SET_RUNNING_SCHEDULED);
            for (ProcessController controller : running.values()) {
                controller.detach();
            }
        }
    }

    /**
     * TESTING only: Returns all process controllers belonging to the given tag.
     */
    public ProcessList getProcessList(String tag) {
        return processMap.get(tag);
    }

    /**
     * Writes the given text to the standard input of the given process.
     *
     * @param applicationId the application identifier
     * @param data the data to write
     */
    public void writeToStdin(String applicationId, String data) {
        ProcessController process = findProcessController(applicationId, SET_RUNNING);
        if (process == null) {
            throw new PcuRuntimeException("Application not running");
        }
        process.writeToStdin(data);
    }

    /**
     * Returns the process controller responsible for the given application.
     *
     * @param applicationId
     *            the application identifier
     * @param filters
     *            find processes matching the given state
     * @return the controller or {@code null} if the application is not running
     */
    private ProcessController findProcessController(String applicationId, Set<ProcessState> filters) {
        for (ProcessList list : processMap.values()) {
            Map<String, ProcessController> app2Controller = list.getWithState(filters);
            ProcessController processController = app2Controller.get(applicationId);
            if (processController != null) {
                return processController;
            }
        }
        return null;
    }

}
