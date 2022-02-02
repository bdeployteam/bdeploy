package io.bdeploy.pcu;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.collect.Sets;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryManager;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

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

    /** Guards access to the map */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    /** Maps the tag of an instance to the list of processes */
    private final Map<String, ProcessList> processMap = new HashMap<>();

    /** The currently active tag */
    private String activeTag;

    /** The instance UUID */
    private final String instanceUid;

    /**
     * Create a new instance controller.
     */
    public InstanceProcessController(String instanceUid) {
        this.instanceUid = instanceUid;
        this.logger.setMdcValue(instanceUid);
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
        try {
            writeLock.lock();
            // Create a new list if not yet existing for this tag
            ProcessList processList = processMap.get(tag);
            if (processList == null) {
                processList = new ProcessList(tag, groupConfig);
                processMap.put(tag, processList);
            }

            // Fetch all process control groups which are available.
            processList.setControlGroupsFromConfig(inm);

            // Add a new controller for each application
            for (ProcessConfiguration config : groupConfig.applications) {
                Path processDir = pathProvider.get(SpecialDirectory.RUNTIME).resolve(config.uid);
                ProcessController controller = new ProcessController(groupConfig.uuid, tag, config, processDir);
                CompositeResolver resolverWithApp = new CompositeResolver();
                if (inm != null) {
                    resolverWithApp.add(new ApplicationParameterValueResolver(config.uid, inm.getConfiguration()));
                }
                resolverWithApp.add(resolver);
                controller.setVariableResolver(resolverWithApp);

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
                        runtimeHistory.record(status.pid, status.exitCode, status.processState, config.name, event.user);
                    });
                }

                processList.add(controller);
                logger.log(l -> l.debug("Creating new process controller."), tag, config.uid);
            }
        } finally {
            writeLock.unlock();
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
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                int running = list.recover();
                if (running == 0) {
                    continue;
                }
                tag2Running.put(list.getInstanceTag(), running);
            }
        } finally {
            readLock.unlock();
        }

        // Print out a summary. Indicate if applications across different versions are running
        if (tag2Running.isEmpty()) {
            logger.log(l -> l.info("No applications are running."));
        } else if (tag2Running.size() == 1) {
            int counter = tag2Running.values().iterator().next();
            logger.log(l -> l.info("{} application(s) are running.", counter));
        } else {
            int counter = tag2Running.values().stream().mapToInt(Integer::intValue).sum();
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
        try {
            readLock.lock();
            ProcessList list = processMap.get(activeTag);
            if (!list.processConfig.autoStart) {
                logger.log(l -> l.info("Autostart not configured. Applications remain stopped."), activeTag);
                return;
            }
            logger.log(l -> l.info("Auto-Starting applications."), activeTag);
        } finally {
            readLock.unlock();
        }

        // Proceed with normal startup of all processes
        startAll(null);
    }

    /**
     * Starts all applications of the currently active tag.
     *
     * @param user the user who triggered the stop - null for default user
     */
    public void startAll(String user) {
        if (activeTag == null) {
            throw new PcuRuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();

            // Ensure that something is deployed
            ProcessList list = processMap.get(activeTag);
            if (list == null) {
                throw new PcuRuntimeException("Activated version '" + activeTag + "' is not deployed");
            }
            // Compute runtime state across all versions
            Map<String, ProcessController> running = new HashMap<>();
            for (ProcessList anyList : processMap.values()) {
                running.putAll(anyList.getWithState(SET_RUNNING_SCHEDULED));
            }

            // Start all missing applications
            logger.log(l -> l.info("Starting all applications."), activeTag);

            // Let the desired strategy start all processes
            BulkProcessController strategy = new BulkProcessController(instanceUid, activeTag, list);
            strategy.startAll(user, running);
        } finally {
            readLock.unlock();
        }
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
        Map<String, ProcessController> running = new TreeMap<>();
        for (ProcessList list : processMap.values()) {
            Map<String, ProcessController> runningScheduled = list.getWithState(SET_RUNNING_SCHEDULED);
            running.putAll(runningScheduled);
        }

        logger.log(l -> l.info("Stopping all running applications."));

        BulkProcessController strategy = new BulkProcessController(instanceUid, activeTag, processMap.get(activeTag));
        strategy.stopAll(user, running);
    }

    /**
     * Starts the latest activated version of the given application.
     *
     * @param applicationId
     *            the application identifier
     * @param user the user who triggered the start
     */
    public void start(String applicationId, String user) {
        if (activeTag == null) {
            throw new PcuRuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(SET_RUNNING);
                if (running.containsKey(applicationId)) {
                    ProcessStatusDto dto = running.get(applicationId).getStatus();
                    throw new PcuRuntimeException(
                            "Application '" + dto.appUid + "' already running in version '" + dto.instanceTag + "'");
                }
            }
        } finally {
            readLock.unlock();
        }

        // Start the latest version
        try {
            ProcessList list = processMap.get(activeTag);
            ProcessController controller = list.get(applicationId);
            controller.start(user);
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to start application", ex), activeTag, applicationId);
        }
    }

    /**
     * Stops the given application.
     *
     * @param applicationId
     *            the application identifier
     * @param user the user who triggered the stop
     */
    public void stop(String applicationId, String user) {
        ProcessController process = findProcessController(applicationId, SET_RUNNING_SCHEDULED);
        if (process == null) {
            throw new PcuRuntimeException("Application not running");
        }
        try {
            process.stop(user);
        } catch (Exception ex) {
            String tag = process.getStatus().instanceTag;
            logger.log(l -> l.error("Failed to stop application", ex), tag, applicationId);
        }
    }

    /**
     * Returns runtime details about this instance node.
     *
     * @return information about running applications.
     */
    public InstanceNodeStatusDto getStatus() {
        try {
            readLock.lock();
            InstanceNodeStatusDto status = new InstanceNodeStatusDto();
            status.activeTag = activeTag;
            for (Map.Entry<String, ProcessList> entry : processMap.entrySet()) {
                status.add(entry.getKey(), entry.getValue().getStatus());
            }
            return status;
        } finally {
            readLock.unlock();
        }
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
     * Detaches all running applications so that they cannot be controlled any more.
     * Only useful in JUNIT tests.
     */
    public void detach() {
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(SET_RUNNING_SCHEDULED);
                for (ProcessController controller : running.values()) {
                    controller.detach();
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns all process controllers belonging to the given tag.
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
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> app2Controller = list.getWithState(filters);
                ProcessController processController = app2Controller.get(applicationId);
                if (processController != null) {
                    return processController;
                }
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

}
