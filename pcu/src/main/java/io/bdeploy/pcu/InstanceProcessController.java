package io.bdeploy.pcu;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryManager;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.pcu.util.Formatter;

/**
 * Manages all processes of a given instance and knows which processes from which version are running. The controller will
 * instantiate a new {@linkplain ProcessController process controller} for each instance.
 */
public class InstanceProcessController {

    /** States that indicate that the process is running */
    private static final Set<ProcessState> SET_RUNNING = Sets.immutableEnumSet(ProcessState.RUNNING,
            ProcessState.RUNNING_UNSTABLE);

    /** States that indicate that the process is running or scheduled */
    private static final Set<ProcessState> SET_RUNNING_SCHEDULED = Sets.immutableEnumSet(ProcessState.RUNNING,
            ProcessState.RUNNING_UNSTABLE, ProcessState.CRASHED_WAITING);

    private final MdcLogger logger = new MdcLogger(InstanceProcessController.class);

    /** Guards access to the map */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    /** Maps the tag of an instance to the list of processes */
    private final Map<String, ProcessList> processMap = new HashMap<>();

    /** The currently active tag */
    private String activeTag;

    /** Provides the order of processes */
    private Function<String, List<String>> orderSupplier;

    /**
     * Create a new instance controller.
     */
    public InstanceProcessController(String instanceUid) {
        this.logger.setMdcValue(instanceUid);
    }

    /**
     * Creates new process controllers for all applications defined in the process group and adds a listener for runtime events.
     *
     * @param pathProvider provides access to the special folders
     * @param resolver the resolver for variables
     * @param tag version of the configuration
     * @param groupConfig the process configuration
     * @param runtimeHistory optional {@link MinionRuntimeHistoryManager} to write to.
     */
    public void createProcessControllers(DeploymentPathProvider pathProvider, VariableResolver resolver, String tag,
            ProcessGroupConfiguration groupConfig, MinionRuntimeHistoryManager runtimeHistory) {
        try {
            writeLock.lock();
            // Create a new list if not yet existing for this tag
            ProcessList processList = processMap.get(tag);
            if (processList == null) {
                processList = new ProcessList(tag, groupConfig);
                processMap.put(tag, processList);
            }

            // Add a new controller for each application
            for (ProcessConfiguration config : groupConfig.applications) {
                Path processDir = pathProvider.get(SpecialDirectory.RUNTIME).resolve(config.uid);
                ProcessController controller = new ProcessController(groupConfig.uuid, tag, config, processDir);
                controller.setVariableResolver(resolver);

                if (runtimeHistory != null) {
                    controller.addStatusListener(state -> runtimeHistory.record(controller.getPID(), state, config.name));
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
     * Sets the supplier that is asked for the current order in which the processes are configured. This
     * order is taken into account when starting and when stopping processes. Stopping is done by reversing
     * the provided list and then stop one process after each other. If no provider is set then processes are started
     * and stopped in an undefined order.
     *
     * @param orderSupplier takes the current active tag and returns the order of the processes
     */
    public void setOrderProvider(Function<String, List<String>> orderSupplier) {
        this.orderSupplier = orderSupplier;
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
        startAll();
    }

    /**
     * Starts all applications of the currently active tag.
     */
    public void startAll() {
        if (activeTag == null) {
            throw new PcuRuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();

            // Compute runtime state across all versions
            Map<String, ProcessController> running = new HashMap<>();
            for (ProcessList list : processMap.values()) {
                running.putAll(list.getWithState(SET_RUNNING_SCHEDULED));
            }

            // Ensure that something is deployed
            ProcessList list = processMap.get(activeTag);
            if (list == null) {
                throw new PcuRuntimeException("Activated version '" + activeTag + "' is not deployed");
            }

            // Determine order in which processes should be started
            List<String> startupOrder = new ArrayList<>();
            if (orderSupplier != null) {
                startupOrder.addAll(orderSupplier.apply(activeTag));
            } else {
                startupOrder.addAll(list.controllers.keySet());
            }

            // Start all missing applications
            logger.log(l -> l.info("Starting all applications."), activeTag);
            Instant start = Instant.now();
            List<String> failed = new ArrayList<>();
            for (String appId : startupOrder) {
                ProcessController controller = list.get(appId);
                if (controller == null) {
                    continue;
                }

                // Write logs when the application is already running
                if (running.containsKey(appId)) {
                    ProcessStatusDto data = controller.getStatus();
                    if (data.instanceTag.equals(activeTag)) {
                        logger.log(l -> l.warn("Application already running in a different version."), data.instanceTag,
                                data.appUid);
                    } else {
                        logger.log(l -> l.info("Application already running."), data.instanceTag, data.appUid);
                    }
                    continue;
                }

                // Only start when auto-start is configured
                ProcessConfiguration config = controller.getDescriptor();
                if (config.processControl.startType != ApplicationStartType.INSTANCE) {
                    logger.log(l -> l.info("Application does not have 'instance' start type set."), activeTag, appId);
                    continue;
                }

                // Start it
                try {
                    controller.start();
                } catch (Exception ex) {
                    failed.add(appId);
                    logger.log(l -> l.info("Failed to start application.", ex), activeTag, appId);
                }
            }
            Duration duration = Duration.between(start, Instant.now());
            if (failed.isEmpty()) {
                logger.log(l -> l.info("Applications have been started in {}", Formatter.formatDuration(duration)), activeTag);
                return;
            }
            logger.log(l -> l.warn("Not all applications could be started. Failed {}", failed));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Stops all running processes of this instance. Processes are stopped in the order that is defined by the given list. If a
     * process is running but it is not contained in this list then it is stopped before all other processes. If the order is
     * undefined for multiple processes then the order is not guaranteed.
     */
    public void stopAll() {
        // Determine all running versions across all tags
        Map<String, ProcessController> running = new HashMap<>();
        for (ProcessList list : processMap.values()) {
            running.putAll(list.getWithState(SET_RUNNING_SCHEDULED));
        }

        // Determine order in which processes should be stopped
        List<String> shutdownOrder = new ArrayList<>();
        if (orderSupplier != null) {
            shutdownOrder.addAll(orderSupplier.apply(activeTag));
            Collections.reverse(shutdownOrder);
        } else {
            List<String> unordered = running.values().stream().map(pc -> pc.getDescriptor().uid).collect(Collectors.toList());
            shutdownOrder.addAll(unordered);
        }

        // Bring the controllers in the desired order
        Deque<ProcessController> toStop = new LinkedList<>();
        for (String appUid : shutdownOrder) {
            ProcessController controller = running.remove(appUid);
            if (controller == null) {
                continue;
            }
            toStop.add(controller);
        }
        // Unknown processes are stopped first
        running.values().forEach(toStop::addFirst);

        // Set intend that all should be stopped
        for (ProcessController process : toStop) {
            try {
                process.prepareStop();
            } catch (Exception ex) {
                String appId = process.getDescriptor().uid;
                String tag = process.getStatus().instanceTag;
                logger.log(l -> l.error("Failed to prepare stopping of application.", ex), tag, appId);
            }
        }

        // Execute shutdown in new thread so that the caller is not blocked
        Instant start = Instant.now();
        Iterator<ProcessController> iter = toStop.iterator();
        logger.log(l -> l.info("Stopping all running applications."));
        while (iter.hasNext()) {
            ProcessController process = iter.next();
            try {
                process.stop();
                iter.remove();
            } catch (Exception ex) {
                String appId = process.getDescriptor().uid;
                String tag = process.getStatus().instanceTag;
                logger.log(l -> l.error("Failed to stop application.", ex), tag, appId);
            }
        }
        // Check if we could stop all applications
        Duration duration = Duration.between(start, Instant.now());
        if (toStop.isEmpty()) {
            logger.log(l -> l.info("Applications have been stopped in {} ", Formatter.formatDuration(duration)));
            return;
        }
        String stillRunning = toStop.stream().map(pc -> pc.getDescriptor().name).collect(Collectors.joining(","));
        logger.log(l -> l.warn("Not all applications could be stopped."));
        logger.log(l -> l.warn("Following applications are still running or in an undefined state: {}", stillRunning));
    }

    /**
     * Starts the latest activated version of the given application.
     *
     * @param applicationId
     *            the application identifier
     */
    public void start(String applicationId) {
        if (activeTag == null) {
            throw new PcuRuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(SET_RUNNING_SCHEDULED);
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
            controller.start();
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to start application", ex), activeTag, applicationId);
        }
    }

    /**
     * Stops the given application.
     *
     * @param applicationId
     *            the application identifier
     */
    public void stop(String applicationId) {
        ProcessController process = findProcessController(applicationId, SET_RUNNING_SCHEDULED);
        if (process == null) {
            throw new PcuRuntimeException("Application not running");
        }
        try {
            process.stop();
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
