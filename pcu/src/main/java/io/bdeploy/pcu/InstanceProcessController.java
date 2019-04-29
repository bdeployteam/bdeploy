package io.bdeploy.pcu;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.ws.rs.WebApplicationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.pcu.util.Formatter;

/**
 * Manages all processes of a given instance and knows which processes from which version are running. The controller will
 * instantiate a new {@linkplain ProcessController process controller} for each instance.
 */
public class InstanceProcessController {

    private static final Logger log = LoggerFactory.getLogger(InstanceProcessController.class);

    /** Guards access to the map */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    /** The unique ID of the instance */
    private final String instanceUid;

    /** Maps the tag of an instance to the list of processes */
    private final Map<String, ProcessList> processMap = new HashMap<>();

    /** The currently active tag */
    private String activeTag;

    /**
     * Create a new instance controller.
     *
     * @param instanceUid the unique id of the instance
     */
    public InstanceProcessController(String instanceUid) {
        this.instanceUid = instanceUid;
    }

    /**
     * Adds the given process group to the list of processes.
     *
     * @param pathProvider
     *            provides access to the special folders
     * @param tag
     *            version of the configuration
     * @param groupConfig
     *            the process configuration
     */
    public void addProcessGroup(DeploymentPathProvider pathProvider, String tag, ProcessGroupConfiguration groupConfig) {
        try {
            writeLock.lock();
            // Create a new list if not yet existing for this tag
            ProcessList processList = processMap.get(tag);
            if (processList == null) {
                processList = new ProcessList(tag, groupConfig);
                processMap.put(tag, processList);
            }

            // Add a new controller for each application
            for (ProcessConfiguration pc : groupConfig.applications) {
                Path processDir = pathProvider.get(SpecialDirectory.RUNTIME).resolve(pc.uid);
                processList.add(new ProcessController(groupConfig.uuid, tag, pc, processDir));
                log.info(buildAppLogString("Creating new process controller.", tag, pc.uid));
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
        log.info(buildLogString("Setting active tag to %s.", activeTag));
        this.activeTag = activeTag;
    }

    /**
     * Checks which applications of this instance are still running.
     */
    public void recover() {
        Map<String, Integer> tag2Running = new TreeMap<>();
        log.info(buildLogString("Checking which applications are alive."));
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
            log.info(buildLogString("No applications are running."));
        } else if (tag2Running.size() == 1) {
            int counter = tag2Running.values().iterator().next();
            log.info(buildLogString("%s application(s) are running.", counter));
        } else {
            int counter = tag2Running.values().stream().mapToInt(Integer::intValue).sum();
            log.info(buildLogString("%s application(s) from multiple different versions are running.", counter));
        }
    }

    /**
     * Starts all applications of the currently active tag that have the auto-start flag set.
     */
    public void autoStart() {
        if (activeTag == null) {
            log.info(buildLogString("Autostart not possible. No active tag has been set."));
            return;
        }
        // Check auto-start flag of instance
        try {
            readLock.lock();
            ProcessList list = processMap.get(activeTag);
            if (!list.processConfig.autoStart) {
                log.info(buildTagLogString("Autostart not configured. Applications remain stopped.", activeTag));
                return;
            }
            log.info(buildTagLogString("Auto-Starting applications.", activeTag));
        } finally {
            readLock.unlock();
        }

        // Proceed with normal startup of all processes
        start();
    }

    /**
     * Starts all applications of the currently active tag.
     */
    public void start() {
        if (activeTag == null) {
            throw new RuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();
            log.info(buildTagLogString("Starting all applications.", activeTag));

            // Compute runtime state across all versions
            Map<String, ProcessController> running = new HashMap<>();
            for (ProcessList list : processMap.values()) {
                running.putAll(list.getWithState(ProcessState.SET_RUNNING_SCHEDULED));
            }

            // Start all missing applications
            ProcessList list = processMap.get(activeTag);
            if (list == null) {
                throw new RuntimeException("Activated version '" + activeTag + "' is not deployed");
            }
            for (Map.Entry<String, ProcessController> entry : list.controllers.entrySet()) {
                String appId = entry.getKey();
                ProcessController controller = entry.getValue();

                // Write logs when the application is already running
                if (running.containsKey(appId)) {
                    ProcessStatusDto data = controller.getStatus();
                    if (data.instanceTag.equals(activeTag)) {
                        log.warn(buildAppLogString("Application already running in a different version.", data.instanceTag,
                                data.appUid));
                    } else {
                        log.info(buildAppLogString("Application already running.", data.instanceTag, data.appUid));
                    }
                    continue;
                }

                // Only start when auto-start is configured
                ProcessConfiguration config = controller.getDescriptor();
                if (config.processControl.startType != ApplicationStartType.INSTANCE) {
                    log.info(buildAppLogString("Application does not have 'instance' start type set.", activeTag, appId));
                    continue;
                }

                // Start it
                try {
                    controller.start();
                } catch (Exception ex) {
                    log.error(buildAppLogString("Failed to start application", activeTag, appId), ex);
                }
            }
            log.info(buildTagLogString("All applications have been started.", activeTag));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Stops all running processes.
     */
    public void stop() {
        ExecutorService service = Executors.newFixedThreadPool(4, new NamedDaemonThreadFactory(instanceUid + "-stopProcess"));
        Collection<ProcessController> toStop = new ArrayList<>();
        try {
            readLock.lock();
            // Execute stopping in parallel as this could last for some time
            for (ProcessList list : processMap.values()) {
                for (ProcessController controller : list.getWithState(ProcessState.SET_RUNNING_SCHEDULED).values()) {
                    toStop.add(controller);
                }
            }
        } finally {
            readLock.unlock();
        }

        log.info(buildLogString("Stopping %s running applications.", toStop.size()));
        for (ProcessController process : toStop) {
            service.execute(() -> {
                try {
                    process.stop();
                } catch (Exception ex) {
                    String appId = process.getDescriptor().uid;
                    String tag = process.getStatus().instanceTag;
                    log.error(buildAppLogString("Failed to stop application", tag, appId), ex);
                }
            });
        }

        // Wait for all to terminate
        Instant start = Instant.now();
        log.info(buildLogString("Waiting for applications to stop."));
        try {
            service.shutdown();
            service.awaitTermination(5, TimeUnit.MINUTES);
            Duration duration = Duration.between(start, Instant.now());
            log.info(buildLogString("All applications stopped. Stopping took %s", Formatter.formatDuration(duration)));
        } catch (InterruptedException e) {
            Duration duration = Duration.between(start, Instant.now());
            Thread.currentThread().interrupt();
            throw new WebApplicationException(
                    "Interruped while waiting for processes to stop. Waited for " + Formatter.formatDuration(duration), e);
        }

    }

    /**
     * Starts the latest activated version of the given application.
     *
     * @param applicationId
     *            the application identifier
     */
    public void start(String applicationId) {
        if (activeTag == null) {
            throw new RuntimeException("No active tag has been set");
        }
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(ProcessState.SET_RUNNING);
                if (running.containsKey(applicationId)) {
                    ProcessStatusDto dto = running.get(applicationId).getStatus();
                    throw new RuntimeException(
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
            log.error(buildAppLogString("Failed to start application", activeTag, applicationId), ex);
        }
    }

    /**
     * Stops the given application.
     *
     * @param applicationId
     *            the application identifier
     */
    public void stop(String applicationId) {
        // Find the process that is running
        ProcessController process = null;
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(ProcessState.SET_RUNNING_SCHEDULED);
                process = running.get(applicationId);
                if (process == null) {
                    continue;
                }
                break;
            }
        } finally {
            readLock.unlock();
        }

        // Throw if we cannot find a running process
        if (process == null) {
            throw new RuntimeException("Application not running");
        }
        try {
            process.stop();
        } catch (Exception ex) {
            String tag = process.getStatus().instanceTag;
            log.error(buildAppLogString("Failed to stop application", tag, applicationId), ex);
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
            for (Map.Entry<String, ProcessList> entry : processMap.entrySet()) {
                status.add(entry.getKey(), entry.getValue().getStatus());
            }
            status.activeTag = activeTag;
            return status;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Detaches all running applications so that they cannot be controlled any more.
     * Only useful in JUNIT tests.
     */
    public void detach() {
        try {
            readLock.lock();
            for (ProcessList list : processMap.values()) {
                Map<String, ProcessController> running = list.getWithState(ProcessState.SET_RUNNING_SCHEDULED);
                for (ProcessController controller : running.values()) {
                    controller.detach();
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    /** Prefixes the given message with the instanceUid */
    private String buildLogString(String message, Object... args) {
        String prefix = String.format("%s - ", instanceUid);
        return prefix + String.format(message, args);
    }

    /** Prefixes the given message with the instanceUid and tag */
    private String buildTagLogString(String message, String tag, Object... args) {
        String prefix = String.format("%s / %s - ", instanceUid, tag);
        return prefix + String.format(message, args);
    }

    /** Prefixes the given message with the instanceUid, tag and application ID */
    private String buildAppLogString(String message, String tag, String appId, Object... args) {
        String prefix = String.format("%s / %s / %s - ", instanceUid, tag, appId);
        return prefix + String.format(message, args);
    }

}
