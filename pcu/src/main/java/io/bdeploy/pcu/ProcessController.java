package io.bdeploy.pcu;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.glassfish.jersey.client.ClientProperties;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessProbeResultDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessProbeResultDto.ProcessProbeType;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpEndpointType;
import io.bdeploy.interfaces.descriptor.application.LivenessProbeDescriptor;
import io.bdeploy.interfaces.descriptor.application.StartupProbeDescriptor;
import io.bdeploy.interfaces.endpoints.CommonEndpointHelper;
import io.bdeploy.logging.process.RollingStreamGobbler;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.Response;

/**
 * Manages a single process.
 * <p>
 * <b>Keep Alive: </b>
 * The controller will re-start the application as soon as it crashed when the 'keep-alive' flag is set in the
 * {@link ProcessControlConfiguration configuration}. Each restart attempt is executed after an increased timeout.
 * The state of such a faulty application will be set to CRASHED_WAITING while it is waiting to be re-launched
 * and it is set to RUNNING_UNSTABLE after it has been started. The process remains in that state until
 * it is up for some time. An application that keeps crashing will remain stopped and its final state will be CRASHED_PERMANENTLY.
 * </p>
 * <p>
 * <b>Threading:</b> This class is thread safe. A lock prevents that multiple threads can modify the state of a process
 * concurrently. A request will fail immediately if the lock is held by another thread. Locks are released once the method call
 * returns.
 * </p>
 */
public class ProcessController {

    /** Name of the file stored on-disk. Holds information to re-attach to the process */
    private static final String JSON_FILE = "app.json";

    /** Shown user on automated execution */
    private static final String DEFAULT_USER = ApiAccessToken.SYSTEM_USER;

    /** In production, by default, don't wait for out.txt file lock */
    private static boolean lockWait = false;

    private final MdcLogger logger = new MdcLogger(ProcessController.class);
    private final Path processDir;
    private final String instanceId;
    private final String instanceTag;
    private final ProcessConfiguration processConfig;

    /** Lock that is used to guard start, stop and termination */
    private final ReentrantLock lock = new ReentrantLock();

    /** The active task that has the lock */
    private String lockTask = null;

    /** Executor used to schedule re-launching of application */
    private final ScheduledExecutorService executorService;

    /** Task scheduled to monitor the up-time */
    private Future<?> uptimeTask;

    /** Task scheduled to start the application when crashed */
    private Future<?> recoverTask;

    /** Task scheduled to monitor process startup probe */
    private Future<?> startupTask;

    /** Task scheduled to monitor process liveness probe */
    private Future<?> aliveTask;

    /** The native process. Only used to evaluate exit code */
    private Process process;

    /** The native process handle. Null if not running */
    private ProcessHandle processHandle;

    /** The bridge which transfers output from the application stream to a log file */
    private RollingStreamGobbler processLogger;

    /** The STDIN of process if available, null otherwise */
    private OutputStream processStdin;

    /** Future to test for process termination */
    private CompletableFuture<ProcessHandle> processExit;

    /** The current status of the process */
    private ProcessState processState = ProcessState.STOPPED;

    /** Time indicating when the stop request was initiated */
    private Instant stopRequested = null;

    /** Time when the process has been started */
    private Instant startTime;

    /** Exit code when the process terminated. NULL in case we do not have one */
    private Integer exitCode;

    /** Time when the process was stopped / crashed */
    private Instant stopTime;

    /** Configured threshold for the application to get back to normal running */
    private Duration stableThreshold = Duration.ofMinutes(5);

    /** Delays in seconds between restarts after the application crashes */
    private Duration[] recoverDelays = { Duration.ofSeconds(0), Duration.ofSeconds(10), Duration.ofSeconds(30),
            Duration.ofSeconds(60) };

    /** Number of times to retry starting after the application crashes */
    private int recoverAttempts = 3;

    /** Number of times we tried to recover the application */
    private int recoverCount = 0;

    /** Listener that is notified when the state changes */
    private final List<Consumer<ProcessStateChangeDto>> statusListeners = new ArrayList<>();

    /** Replace variables used in the start/stop command and it's arguments */
    private VariableResolver variableResolver;

    /** The results of the last probe calls */
    private final Map<ProcessProbeType, ProcessProbeResultDto> lastProbeResults = new EnumMap<>(ProcessProbeType.class);

    /**
     * Creates a new process controller for the given configuration.
     *
     * @param instanceId
     *            unique identifier of the parent instance
     * @param instanceTag
     *            unique identifier of the parent instance version
     * @param pc
     *            the configuration of the process to launch
     * @param processDir
     *            the "runtime" directory, used for data specific to this
     *            launch
     */
    public ProcessController(String instanceId, String instanceTag, ProcessConfiguration pc, Path processDir) {
        this.logger.setMdcValue(instanceId, instanceTag, pc.id);
        this.instanceId = instanceId;
        this.instanceTag = instanceTag;
        this.processConfig = pc;
        this.recoverAttempts = pc.processControl.noOfRetries;
        this.processDir = processDir;
        this.executorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name(processConfig.id).factory());
    }

    @Override
    public String toString() {
        ProcessStatusDto status = getStatus();
        List<String> logs = new ArrayList<>();
        logs.addAll(status.logStatusDetails());
        logs.add("Alive: " + (processHandle != null ? processHandle.isAlive() : Boolean.FALSE.toString()));
        if (processHandle != null) {
            logs.add("Children: " + processHandle.children().count());
        }
        logs.add("Directory: " + processDir);
        logs.add("Active Task: " + (lockTask != null ? lockTask : "-"));
        logs.add("Stop Requested: " + stopRequested);
        logs.add("Uptime Task: " + (uptimeTask != null ? !uptimeTask.isDone() : Boolean.FALSE.toString()));
        logs.add("Recover Task: " + (recoverTask != null ? !recoverTask.isDone() : Boolean.FALSE.toString()));
        return String.join("\n", logs);
    }

    /**
     * Registers a new listener that is notified whenever the status of an application changes.
     *
     * @param listener
     *            the listener to notify
     */
    public void addStatusListener(Consumer<ProcessStateChangeDto> listener) {
        this.statusListeners.add(listener);
    }

    public void removeStatusListener(Consumer<ProcessStateChangeDto> listener) {
        this.statusListeners.remove(listener);
    }

    /**
     * Configures the number of restart attempts that are executed if the application crashed. The delay between
     * two consecutive restart attempts is configured separately. When the restart counter is higher than the given
     * number of attempts than the application remains terminated. Zero means that the controller will never give
     * up restarting the application.
     *
     * @param attempts the number of attempts to execute.
     */
    public void setRecoverAttempts(int attempts) {
        this.recoverAttempts = attempts;
    }

    /**
     * Configures the duration how long to wait between two consecutive restart attempts. For each restart attempt
     * the corresponding delay will be used. The highest delay will be used if the recover counter is higher than the configured
     * number of delays.
     * <p>
     * Default: 0 seconds, 10 seconds, 30 seconds, 60 seconds
     * </p>
     *
     * <pre>
     *          Attempts =  10
     *          Delays   =  1 sec, 2 sec, 5 sec, 10 sec
     *
     *          Restart #1 ->  1 sec
     *          Restart #2 ->  2 sec
     *          Restart #3 ->  5 sec
     *          Restart #4 ->  10 sec
     *          Restart #5 ->  10 sec
     *          ...
     *          Restart #10 -> 10 sec
     * </pre>
     *
     * @param recoverDelays the recover delays
     */
    public void setRecoverDelays(Duration... recoverDelays) {
        this.recoverDelays = recoverDelays;
    }

    /**
     * Sets the duration how long an application must be running after it crashed to become stable again.
     * <p>
     * Default: The default duration is 5 minutes.
     * </p>
     *
     * @param stableThreshold the duration
     */
    public void setStableThreshold(Duration stableThreshold) {
        this.stableThreshold = stableThreshold;
    }

    /**
     * Sets the resolver that will be used to replace variables in the start and stop command as well as its arguments.
     *
     * @param variableResolver the resolver to be used
     */
    public void setVariableResolver(VariableResolver variableResolver) {
        this.variableResolver = variableResolver;
    }

    /**
     * Returns a DTO containing information about the process status.
     */
    public ProcessStatusDto getStatus() {
        ProcessStatusDto dto = new ProcessStatusDto();
        dto.processState = processState;
        dto.appId = processConfig.id;
        dto.appName = processConfig.name;
        dto.instanceTag = instanceTag;
        dto.instanceId = instanceId;
        if (processHandle != null) {
            dto.pid = processHandle.pid();
        }
        if (exitCode != null) {
            dto.exitCode = exitCode;
        }
        return dto;
    }

    /**
     * Returns details about the running process
     *
     * @return detailed information about the running process
     */
    public ProcessDetailDto getDetails() {
        ProcessDetailDto dto = new ProcessDetailDto();
        dto.maxRetryCount = recoverAttempts;
        if (recoverCount > 0 && stopTime != null) {
            Duration delay = recoverDelays[Math.min(recoverCount, recoverDelays.length - 1)];
            dto.recoverAt = stopTime.plus(delay).toEpochMilli();
            dto.recoverDelay = delay.toSeconds();
        }
        dto.retryCount = recoverCount;
        if (stopTime != null) {
            dto.stopTime = stopTime.toEpochMilli();
        }
        dto.status = getStatus();
        if (processState.isRunning()) {
            dto.hasStdin = processStdin != null;
            dto.handle = ProcessControllerHelper.collectProcessInfo(processHandle);
        }
        dto.lastProbes = new ArrayList<>(lastProbeResults.values());
        return dto;
    }

    /**
     * Returns the process configuration.
     *
     * @return the process configuration
     */
    public ProcessConfiguration getDescriptor() {
        return processConfig;
    }

    /**
     * Returns the state of this process
     */
    public ProcessState getState() {
        return processState;
    }

    /**
     * Starts the process and monitors the execution.
     *
     * @param user the user who triggered the start
     */
    public void start(String user) {
        executeLocked("Start", user, () -> doStart(true));
    }

    /**
     * Sets the intent that starting this process is planned in the near future. This does not have any effect on the process
     * itself. It will still be stopped and starting must be explicitly invoked to really terminate the process. Setting this
     * intent is typically done when starting multiple processes one after each other to visualize the desired target state.
     */
    public void prepareStart(String user) {
        executeLocked("PrepareStart", user, this::doPrepareStart);
    }

    /**
     * Signals that a planned start operations has been aborted for any reason. The process is put back into STOPPED state.
     */
    public void abortStart(String user) {
        executeLocked("AbortStart", user, this::doAbortStart);
    }

    /**
     * Sets the intent that stopping this process is planned in the near future. This does not have any effect on the process
     * itself. It will still be running and stopping must be explicitly invoked to really terminate the process. Setting this
     * intent is typically done when stopping multiple processes one after each other to visualize the desired target state.
     */
    public void prepareStop(String user) {
        executeLocked("PrepareStop", user, this::doPrepareStop);
    }

    /**
     * Stops the process and all its descendants.
     * <p>
     * <b>Procedure:</b> If a stop command is defined this command is executed. It is expected that the process
     * terminates after the defined grace period. If the grace period is exceeded or if there is no stop command
     * then the process is {@linkplain Process#destroy() destroyed} and if that does not help it is
     * {@linkplain Process#destroyForcibly() forcibly destroyed}.
     * </p>
     * <p>
     * When this method returns the process is either terminated or still running because the process did not react to the
     * shutdown requests. If that happens then the grace-period of the application must be extended or the application must be
     * fixed to shutdown faster.
     * </p>
     *
     * @param user the user who triggered the stop
     */
    public void stop(String user) {
        executeLocked("Stop", user, this::doStop);
    }

    /**
     * Tries to determine whether or not a process for this application is still running. This is done
     * by reading the JSON file written when the application was launched. Status is changed to RUNNING
     * if recovering was successfully. Status remains STOPPED if the process is not alive any more.
     */
    public void recover() {
        executeLocked("Recover", DEFAULT_USER, this::doRecover);
    }

    /**
     * Keeps the application running in the current state and discards
     * all information that this controller has. Only useful in UNIT tests.
     */
    public void detach() {
        executeLocked("Detach", DEFAULT_USER, this::doDetach);
    }

    /**
     * Writes the given data to the standard input of the process.
     *
     * @param data the data to write
     */
    public void writeToStdin(String data) {
        if (processStdin == null) {
            logger.log(l -> l.error("STDIN not available."));
            return;
        }
        try {
            String tmp = data + System.lineSeparator();
            processStdin.write(tmp.getBytes());
            processStdin.flush();
        } catch (IOException e) {
            logger.log(l -> l.error("Failed to write to STDIN.", e));
        }
    }

    /** Starts the application */
    private void doStart(boolean resetRecoverCount) {
        // Do nothing if already started
        if (processState.isRunning()) {
            logger.log(l -> l.info("Skipping start of {} because it is already running", processConfig.name));
            return;
        }
        logger.log(l -> l.info("Starting {}", processConfig.name));

        // Reset counter if manually started
        if (resetRecoverCount) {
            recoverCount = 0;
        }

        // Create runtime directory if missing
        if (!Files.isDirectory(processDir)) {
            PathHelper.mkdirs(processDir);
        }

        // Clean up any previous info file
        Path infoFile = processDir.resolve(JSON_FILE);
        PathHelper.deleteRecursiveRetry(infoFile);

        // Cancel any pending restart tasks
        exitCode = null;
        stopTime = null;
        stopRequested = null;

        doCancelProbeTasks();

        try {
            process = launch(processConfig.start, processConfig.startEnv);
            processHandle = process.toHandle();
            processStdin = process.getOutputStream();
            processLogger = new RollingStreamGobbler(processDir, process, instanceId, processConfig.id);
            processExit = processHandle.onExit();
            startTime = processHandle.info().startInstant().orElseGet(() -> {
                logger.log(l -> l.error("Start time of process not available, falling back to current time. PID = {}.",
                        processHandle.pid()));
                return Instant.now();
            });

            // start pumping the streams.
            processLogger.start();

            // Persist the process that we just started to recover it if required
            ProcessControllerDto dto = new ProcessControllerDto();
            dto.pid = processHandle.pid();
            dto.startTime = ProcessControllerHelper.getProcessStartTimestampCorrected(logger, processHandle, startTime);
            Files.write(infoFile, StorageHelper.toRawBytes(dto), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);
            logger.log(l -> l.info("Successfully started application. PID = {}.", dto.pid));

            // Attach exit handle to get notified about termination
            monitorProcess();
        } catch (IOException e) {
            logger.log(l -> l.error("Failed to launch application.", e));
            processState = ProcessState.CRASHED_PERMANENTLY;
            cleanup();
        }
    }

    /** Updates the state that starting is planned */
    private void doPrepareStart() {
        if (!processState.isStopped()) {
            return;
        }

        processState = ProcessState.STOPPED_START_PLANNED;
        logger.log(l -> l.info("Starting planned for {}", processConfig.name));
    }

    /** Updates the state that starting is planned */
    private void doAbortStart() {
        if (!processState.isStopped()) {
            return;
        }

        processState = ProcessState.STOPPED;
        logger.log(l -> l.info("Starting aborted for {}", processConfig.name));
    }

    /** Updates the state that stopping is planned */
    private void doPrepareStop() {
        if (!processState.isRunning()) {
            return;
        }

        doCancelProbeTasks();

        logger.log(l -> l.info("Stopping planned for {}, current state: {}", processConfig.name, processState));
        processState = ProcessState.RUNNING_STOP_PLANNED;
    }

    /** Stops the application */
    private void doStop() {
        // Do nothing if already stopped
        if (processState.isStopped()) {
            logger.log(l -> l.debug("Process already in state {}: {} - doing nothing", processState, processConfig.id));
            return;
        }

        // Set flag that the termination of the process is expected
        stopRequested = Instant.now();
        if (processState == ProcessState.CRASHED_WAITING) {
            logger.log(l -> l.info("Aborting restart attempt of application."));
            processState = ProcessState.STOPPED;
            cleanup();
            return;
        }

        // Stopping could take a while thus we set the intent
        logger.log(l -> l.info("Stopping {}", processConfig.name));
        processState = ProcessState.RUNNING_STOP_PLANNED;

        // try to gracefully stop the process using it's stop command
        doInvokeStopCommand();
        if (processExit.isDone()) {
            onTerminated();
            return;
        }

        // Stop command failed or nothing defined.
        // Go on with destroying the process via the handle
        long stopCommandDuration = Duration.between(stopRequested, Instant.now()).toMillis();
        long gracePeriod = processConfig.processControl.gracePeriod + stopCommandDuration;
        if (!doDestroyProcess(gracePeriod)) {
            processState = ProcessState.RUNNING;
            logger.log(l -> l.error("Giving up to terminate application. Process does not respond to kill requests."));
            logger.log(l -> l.error("Application state remains {} ", processState));
            return;
        }
        onTerminated();
    }

    /** Recovers the application when running */
    private void doRecover() {
        Path pidFile = processDir.resolve(JSON_FILE);
        if (!Files.exists(pidFile)) {
            return;
        }

        ProcessControllerDto dto;
        try {
            // Acquire a process handle for the stored PID
            dto = StorageHelper.fromPath(pidFile, ProcessControllerDto.class);
            Optional<ProcessHandle> ph = ProcessHandle.of(dto.pid);
            if (!ph.isPresent()) {
                PathHelper.deleteRecursiveRetry(pidFile);
                return;
            }

            // Remember handle and exit hook
            processHandle = ph.get();
            processExit = processHandle.onExit();

            // use the logger to inform the user in the output file of the recovery and lost output.
            // don't store the gobbler instance, as we cannot start pumping the streams ever again.
            RollingStreamGobbler.logProcessRecovery(processDir, processHandle, instanceId, processConfig.id);
        } catch (Exception e) {
            logger.log(l -> l.trace("Failed to recover the application", e));
            PathHelper.deleteRecursiveRetry(pidFile);
            return;
        }

        // make sure that this is still the process we're looking for.
        startTime = processHandle.info().startInstant().orElseGet(() -> {
            logger.log(l -> l.warn("Recovering process handle does not have a start time! PID = {}", dto.pid));
            return Instant.now();
        });

        long correctedStart = ProcessControllerHelper.getProcessStartTimestampCorrected(logger, processHandle, startTime);
        if (Long.compare(correctedStart, dto.startTime) == 0) {
            logger.log(l -> l.info("Successfully attached to application. PID = {}.", dto.pid));
            monitorProcess();
        } else {
            logger.log(l -> l.info("Discarding existing process information due to start time mismatch. {} != {}, PID = {}.",
                    startTime, dto.startTime, dto.pid));
            PathHelper.deleteRecursiveRetry(pidFile);
            cleanup();
        }
    }

    /** Discard any information about the running process */
    private void doDetach() {
        statusListeners.clear();
        cleanup();
    }

    /** Cleanup internal members */
    private void cleanup() {
        processHandle = null;
        processStdin = null;

        // Cancel the hook that is notified when the process terminates
        if (processExit != null) {
            processExit.cancel(false);
            processExit = null;
        }

        // Cancel the logger, which is only set in case the process was started by us and not recovered.
        if (processLogger != null) {
            processLogger.close();
            processLogger = null;
        }

        // On Windows, wait for the lock on the out.txt file to be released.
        waitForLockRelease();

        // Cancel restart task and monitoring task
        doCancelMonitorTasks();
    }

    /**
     * Tries to acquire an exclusive lock in order to execute the given runnable. Waits if necessary if another operation is in
     * progress. Notifies all listeners if the process state changes after the runnable has been executed.
     * <p>
     * <b>Implementation note:</b> Only a single process-state change should be performed within a given task execution.
     * State listeners are notified about about changes when the task completes. Thus when a task performs multiple state changes
     * then the listeners are only notified about the final state. In all other cases listeners must be notified manually.
     * </p>
     */
    private void executeLocked(String taskName, String user, Runnable runnable) {
        // Wait until we get the lock
        try {
            if (!lock.tryLock()) {
                logger.log(l -> l.debug("Task '{}' is waiting for operation '{}' to finish.", taskName, lockTask));
                lock.lockInterruptibly();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PcuRuntimeException("Interrupted while waiting to execute task '" + taskName + "'", ie);
        }
        this.lockTask = taskName;

        // Execute and notify when lock is released
        ProcessState oldState = processState;
        try {
            runnable.run();
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to execute task.", ex));
            throw new PcuRuntimeException("Failed to execute task '" + taskName + "'", ex);
        } finally {
            // Fallback to system if no user is there
            if (user == null) {
                user = DEFAULT_USER;
            }

            // Notify listeners when state changes
            if (processState != oldState) {
                notifyListeners(new ProcessStateChangeDto(processState, oldState, user));
            }

            lockTask = null;
            lock.unlock();
        }
    }

    public static void enableWaitForLockRelease(boolean enable) {
        lockWait = enable;
    }

    /**
     * Waits until the process releases the lock that is hold on the out.txt file.
     */
    private void waitForLockRelease() {
        // Only Windows is holding file-locks
        if (OsHelper.getRunningOs() != OperatingSystem.WINDOWS || !lockWait) {
            return;
        }

        // We try for some time to rename the file. If that succeeds
        // the lock held by the process is gone.
        File tmpFile = processDir.resolve(RollingStreamGobbler.OUT_TXT + ".tmp").toFile();
        File outFile = processDir.resolve(RollingStreamGobbler.OUT_TXT).toFile();

        // Lock is typically held for up to 150ms, but *sometimes* a lot longer.
        int retry = 1;
        while (!outFile.renameTo(tmpFile) && retry <= 100) {
            logger.log(l -> l.debug("Waiting for file-lock to be released"));
            Threads.sleep(200);
            retry++;
        }

        // Rename back to the original log-file
        if (tmpFile.exists()) {
            if (!tmpFile.renameTo(outFile)) {
                logger.log(l -> l.warn("Failed to rename output file back to its original name."));
            }
        } else {
            logger.log(l -> l.warn("Process is still holding file-locks on the output file."));
        }
    }

    /** Attaches an exit handle to be notified when the process terminates */
    private void monitorProcess() {
        // We are attaching an async-listener so that the hook is not directly called
        // in case the process already terminated when #monitorProcess is called
        CompletableFuture<ProcessHandle> oldHandle = processExit;
        oldHandle.thenRunAsync(() -> executeLocked("ExitHook", DEFAULT_USER, () -> {
            if (oldHandle != processExit) {
                logger.log(l -> l.debug("Process handle changed. Skipping exit hook."));
                return;
            }
            onTerminated();
        }));

        // Set to running if launched from stopped or crashed
        if (processState.isStopped()) {
            processState = ProcessState.RUNNING_NOT_STARTED;
            logger.log(l -> l.info("Application status is now RUNNING_NOT_STARTED."));
        }

        // Schedule uptime monitor if launched from crashed waiting
        if (processState == ProcessState.CRASHED_WAITING) {
            recoverCount++;

            processState = ProcessState.RUNNING_UNSTABLE;
            logger.log(l -> l.info("Application successfully recovered. Attempts: {}/{}", recoverCount, recoverAttempts));
            logger.log(l -> l.info("Application status is now RUNNING_UNSTABLE."));

            // Periodically watch if the process remains alive
            long rateInSeconds = stableThreshold.dividedBy(10).getSeconds();
            if (rateInSeconds == 0) {
                rateInSeconds = 1;
            }
            uptimeTask = executorService.scheduleAtFixedRate(this::doCheckUptime, rateInSeconds, rateInSeconds, TimeUnit.SECONDS);
            String requiredUptime = ProcessControllerHelper.formatDuration(stableThreshold);
            logger.log(l -> l.info("Application will be marked as stable after: {}", requiredUptime));
        }

        startupTask = executorService.scheduleWithFixedDelay(this::doCheckStarted, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void doCheckStarted() {
        StartupProbeDescriptor probe = processConfig.processControl.startupProbe;

        executeLocked("Probe Startup", DEFAULT_USER, () -> {
            boolean running = false;
            if (probe == null) {
                running = true;
            } else {
                Optional<HttpEndpoint> startupEp = processConfig.endpoints.http.stream()
                        .filter(ep -> ep.id.equals(probe.endpoint)).findFirst();
                if (startupEp.isEmpty() || startupEp.get().type != HttpEndpointType.PROBE_STARTUP) {
                    logger.log(l -> l.warn("Application defined startup probe endpoint {} missing or has wrong type.",
                            probe.endpoint));
                    running = true; // no way to check.
                } else {
                    running = doProbe(ProcessProbeType.STARTUP, 0, startupEp.get());
                }
            }

            if (running) {
                var oldState = processState;
                processState = oldState == ProcessState.RUNNING_UNSTABLE ? ProcessState.RUNNING_UNSTABLE : ProcessState.RUNNING;
                logger.log(l -> l.info("Application status is now {}, was {}.", processState, oldState));

                LivenessProbeDescriptor liveness = processConfig.processControl.livenessProbe;
                if (liveness != null) {
                    Optional<HttpEndpoint> aliveEp = processConfig.endpoints.http.stream()
                            .filter(ep -> ep.id.equals(liveness.endpoint)).findFirst();
                    if (aliveEp.isEmpty() || aliveEp.get().type != HttpEndpointType.PROBE_ALIVE) {
                        logger.log(l -> l.warn("Application defined liveness probe endpoint {} missing or has wrong type.",
                                liveness.endpoint));
                    } else {
                        aliveTask = executorService.scheduleWithFixedDelay(this::doCheckAlive, liveness.initialDelaySeconds,
                                liveness.periodSeconds, TimeUnit.SECONDS);
                    }
                }

                startupTask.cancel(false);
                startupTask = null;
            }
        });
    }

    private void doCheckAlive() {
        LivenessProbeDescriptor probe = processConfig.processControl.livenessProbe;
        Optional<HttpEndpoint> aliveEp = processConfig.endpoints.http.stream().filter(ep -> ep.id.equals(probe.endpoint))
                .findFirst();

        if (aliveEp.isEmpty()) {
            // systemic error, this should never happen as the endpoint has already be checked before scheduling this task.
            throw new PcuRuntimeException("Unexpected error in retrieving liveness endpoint.");
        }

        // Don't do this locked. If a probe blocks, we would like to be able to still stop the process (for example).
        boolean alive = doProbe(ProcessProbeType.LIVENESS, probe.periodSeconds, aliveEp.get());

        executeLocked("Probe Alive", DEFAULT_USER, () -> {
            if (processState == ProcessState.RUNNING && !alive) {
                processState = ProcessState.RUNNING_NOT_ALIVE;
            } else if (processState == ProcessState.RUNNING_NOT_ALIVE && alive) {
                processState = ProcessState.RUNNING;
            }
        });
    }

    private boolean doProbe(ProcessProbeType type, long timeout, HttpEndpoint ep) {
        if (ep == null) {
            logger.log(l -> l.error("Null endpoint to probe {}", type));
            return false;
        }

        try {
            HttpEndpoint processed = CommonEndpointHelper.processEndpoint(variableResolver, ep);
            if (processed == null) {
                logger.log(l -> l.warn("Endpoint not enabled {}, forcing probe success: {}", ep.id, type));
                return true; // regard not-enabled probe as success, otherwise probing will always fail.
            }

            Map<String, Object> properties = new HashMap<>();
            if (timeout > 0) {
                Long actualTimeout = Long.valueOf(timeout * 1000);
                properties.put(ClientProperties.CONNECT_TIMEOUT, actualTimeout);
                properties.put(ClientProperties.READ_TIMEOUT, actualTimeout);
            }

            Builder builder = CommonEndpointHelper.createRequestBuilder(processed, null, properties, Collections.emptyMap());
            Response rs = builder.get();
            String resp = rs.hasEntity() ? rs.readEntity(String.class) : "Empty Response";
            int status = rs.getStatus();

            lastProbeResults.put(type, new ProcessProbeResultDto(type, status, resp, System.currentTimeMillis()));

            // defined as "OK" by kubernetes as well.
            return status >= 200 && status < 400;
        } catch (Exception e) {
            logger.log(l -> l.warn("Failed to process HTTP GET request for endpoint {} of type {}", ep.id, type, e));
            lastProbeResults.put(type, new ProcessProbeResultDto(type, 500, e.toString(), System.currentTimeMillis()));
            return false;
        }
    }

    /** Callback method that is executed when the process terminates */
    private void onTerminated() {
        stopTime = Instant.now();
        Duration uptime = Duration.between(startTime, stopTime);
        String uptimeString = ProcessControllerHelper.formatDuration(uptime);

        doCancelProbeTasks();

        // Try to evaluate the exit code
        exitCode = ProcessControllerHelper.getExitCode(process);
        logger.log(l -> l.info("Application terminated. Exit code: {}; Uptime: {} ", exitCode != null ? exitCode : "N/A",
                uptimeString));

        // Someone requested termination
        if (stopRequested != null || processState == ProcessState.RUNNING_STOP_PLANNED) {
            if (stopRequested != null) {
                Duration stopDuration = Duration.between(stopRequested, Instant.now());
                logger.log(l -> l.info("Stopping took {}", ProcessControllerHelper.formatDuration(stopDuration)));
            }
            logger.log(l -> l.info("Application remains stopped as stop was requested."));
            processState = ProcessState.STOPPED;

            cleanup();
            return;
        }

        // One-Shot process that can terminate at any time
        if (!processConfig.processControl.keepAlive) {
            logger.log(l -> l.info("Application remains stopped as keep-alive is not configured."));

            // Set the state depending on the exit code
            if (exitCode != null && exitCode.intValue() != 0) {
                logger.log(l -> l.info("Setting state to CRASHED as exit code signals failure."));
                processState = ProcessState.CRASHED_PERMANENTLY;
            } else {
                processState = ProcessState.STOPPED;
            }

            cleanup();
            return;
        }

        // Give up when the application keeps crashing
        if (recoverAttempts > 0 && recoverCount >= recoverAttempts) {
            logger.log(l -> l.error("Application remains stopped. Tried to relaunch {} times without success.", recoverCount));
            processState = ProcessState.CRASHED_PERMANENTLY;
            cleanup();
            return;
        }
        Duration delay = recoverDelays[Math.min(recoverCount, recoverDelays.length - 1)];
        processState = ProcessState.CRASHED_WAITING;

        // Schedule restarting of application based on configured delay
        Runnable task = () -> executeLocked("Restart", DEFAULT_USER, this::doRestart);
        if (delay.isZero()) {
            logger.log(l -> l.info("Re-launching application immediatly."));
            recoverTask = executorService.schedule(task, 0, TimeUnit.SECONDS);
        } else {
            logger.log(l -> l.info("Waiting {} before re-launching application.", ProcessControllerHelper.formatDuration(delay)));
            recoverTask = executorService.schedule(task, delay.getSeconds(), TimeUnit.SECONDS);
        }
    }

    /** Starts the application if it is not yet running */
    private void doRestart() {
        // Re-Start the application to try again
        // NOTE: User can manually start while we are waiting
        if (processState == ProcessState.STOPPED) {
            logger.log(l -> l.info("Application has been stopped while in crash-back-off. Doing nothing."));
            return;
        } else if (processState.isRunning()) {
            logger.log(l -> l.info("Application has been started while in crash-back-off. Doing nothing."));
            return;
        }

        // Start the application again
        doStart(false);
    }

    /** Switches the state of a process to running if it is alive for a given time period */
    private void doCheckUptime() {
        executeLocked("CheckUptime", DEFAULT_USER, () -> {
            // Skip monitoring if the process has terminated
            if (processState != ProcessState.RUNNING_UNSTABLE) {
                return;
            }

            // Do nothing if the process is not alive long enough
            Instant now = Instant.now();
            Instant stableAfter = startTime.plus(stableThreshold);
            if (!now.isAfter(stableAfter)) {
                return;
            }

            // Reset counter and set application to stable
            recoverCount = 0;
            processState = ProcessState.RUNNING;
            logger.log(l -> l.info("Uptime threshold reached. Marking as stable again."));
            logger.log(l -> l.info("Application status is now RUNNING."));

            doCancelMonitorTasks();
        });
    }

    /**
     * Launches a new process using the given arguments.
     *
     * @param cmd
     *            the command to execute
     * @return the process handle
     * @throws IOException in case of an error starting the {@link Process}.
     */
    private Process launch(List<String> cmd, Map<String, String> env) throws IOException {
        List<String> command = replaceVariables(cmd);
        logger.log(l -> l.debug("Launching new process {}", command));

        ProcessBuilder b = new ProcessBuilder(command).directory(processDir.toFile());
        if (env != null) {
            b.environment().putAll(env);
        }
        b.redirectErrorStream(true);

        return b.start();
    }

    /** Replaces all variables defined in the given list */
    private List<String> replaceVariables(List<String> input) {
        if (variableResolver == null) {
            return input;
        }
        return TemplateHelper.process(input, variableResolver);
    }

    /** Destroys this process and all its descendants */
    private void destroy(ProcessHandle process) {
        // First kill all processes that might be forked by the root
        process.descendants().forEach(ph -> {
            logger.log(l -> l.info("Terminate child process. PID = {}", ph.pid()));
            ph.destroy();
        });

        // Terminate the process itself
        logger.log(l -> l.info("Terminate main process. PID = {}", process.pid()));
        process.destroy();
    }

    /** Forcibly destroys this process and all its descendants */
    private void destroyForcibly(ProcessHandle process) {
        // First kill all processes that might be forked by the root
        process.descendants().forEach(ph -> {
            logger.log(l -> l.info("Forcibly terminate child process. PID = {}", ph.pid()));
            ph.destroyForcibly();
        });

        // Terminate the process itself
        logger.log(l -> l.info("Forcibly terminate main process. PID = {}", process.pid()));
        process.destroyForcibly();
    }

    /** Executes the configured stop command and waits for the termination */
    private void doInvokeStopCommand() {
        try {
            if (processConfig.stop == null || processConfig.stop.isEmpty()) {
                logger.log(l -> l.debug("No stop command configured."));
                return;
            }
            logger.log(l -> l.info("Invoking configured stop command."));
            logger.log(l -> l.debug("Stop command: {}.", processConfig.stop));

            Process stopProcess = launch(processConfig.stop, processConfig.stopEnv);
            NoThrowAutoCloseable stopLogger = null;

            // either we can (and must) attach to an existing one, or we need a new one in case the process was recovered.
            // the output looks slightly different, but this is way better than getting stuck due to unconsumed output.
            if (processLogger != null) {
                stopLogger = processLogger.attachStopProcess(stopProcess);
            } else {
                // java compiler misses that we're auto-closing the resource in any case through stopLogger
                @SuppressWarnings("resource")
                var gobbler = new RollingStreamGobbler(processDir, stopProcess, instanceId, "STOP-" + processConfig.id);
                gobbler.start();

                stopLogger = gobbler;
            }

            try (var close = stopLogger) {
                // Evaluate exit code of stop command
                boolean exited = stopProcess.waitFor(processConfig.processControl.gracePeriod, TimeUnit.MILLISECONDS);
                if (exited) {
                    int exitValue = stopProcess.exitValue();
                    if (exitValue != 0) {
                        logger.log(l -> l.warn("Stop command exited with non-zero code: {}.", exitValue));
                        return;
                    }
                    waitForMainProcessAfterStopCommand();
                    return;
                }

                // Stop command did not complete within allowed time
                // Kill stop command
                logger.log(l -> l.warn("Stop command did not finish within configured grace period."));
                stopProcess.destroy();
                boolean stopExited = stopProcess.waitFor(200, TimeUnit.MILLISECONDS);
                if (!stopExited) {
                    logger.log(l -> l.warn("Stop command refuses to exit, killing."));
                    stopProcess.destroyForcibly();
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.log(l -> l.error("Failed to execute stop command.", ie));
        } catch (Exception e) {
            logger.log(l -> l.error("Failed to execute stop command.", e));
        }
    }

    /** Waits for the main process to terminate after the stop command has been executed */
    private void waitForMainProcessAfterStopCommand() throws InterruptedException, ExecutionException {
        try {
            logger.log(l -> l.info("Stop command sucessfully executed. Waiting for termination of main process..."));
            long stopCommandDuration = Duration.between(Instant.now(), stopRequested).toMillis();
            long remainingGracePeriod = processConfig.processControl.gracePeriod - stopCommandDuration;
            processExit.get(remainingGracePeriod, TimeUnit.MILLISECONDS);
            logger.log(l -> l.info("Stop command successfully terminated main process."));
        } catch (TimeoutException e) {
            logger.log(l -> l.warn("Main process did not exit after stop grace period."));
        }
    }

    /** Signals the process to terminate and waits for the completion */
    private boolean doDestroyProcess(long gracePeriod) {
        int retryCount = 0;
        boolean stopped = false;
        while (!stopped && retryCount < 10) {
            // Destroy or forcibly destroy based on the retry counter
            if (retryCount == 0) {
                destroy(processHandle);
            } else {
                int logCount = retryCount;
                logger.log(l -> l.warn("Attempt {}: Kill application.", logCount));
                destroyForcibly(processHandle);
            }

            // Wait configured delay to stop
            try {
                processExit.get(gracePeriod, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.log(l -> l.warn("Timed-out waiting for application to exit. Timeout: {} ms", gracePeriod));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.log(l -> l.warn("Interrupted while waiting for application to terminate.", ie));
            } catch (Exception e) {
                logger.log(l -> l.warn("Exception while waiting for application to terminate.", e));
            }

            // Continue when process is still alive.
            stopped = processExit.isDone();
            if (!stopped) {
                retryCount++;
            }
        }
        return stopped;
    }

    /** Cancels potentially running recovery and uptime monitoring tasks */
    private void doCancelMonitorTasks() {
        if (recoverTask != null) {
            recoverTask.cancel(true);
            recoverTask = null;
        }
        if (uptimeTask != null) {
            uptimeTask.cancel(true);
            uptimeTask = null;
        }
    }

    /** Cancels potentially running startup and alive probe tasks */
    private void doCancelProbeTasks() {
        lastProbeResults.clear();

        if (startupTask != null) {
            startupTask.cancel(true);
            startupTask = null;
            lastProbeResults.put(ProcessProbeType.STARTUP, new ProcessProbeResultDto(ProcessProbeType.STARTUP, 0,
                    "Startup probe cancelled.", System.currentTimeMillis()));
        }
        if (aliveTask != null) {
            aliveTask.cancel(true);
            aliveTask = null;
            lastProbeResults.put(ProcessProbeType.LIVENESS, new ProcessProbeResultDto(ProcessProbeType.LIVENESS, 0,
                    "Liveness probe cancelled.", System.currentTimeMillis()));
        }
    }

    /** Notifies all listeners about the of the process */
    private void notifyListeners(ProcessStateChangeDto status) {
        try {
            logger.log(l -> l.debug("Notify listeners about new process state {}.", status.newState));
            new ArrayList<>(statusListeners).forEach(c -> c.accept(status));
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to notify listener about current process status.", ex));
        }
    }

    /** TESTING only: Returns a new-instance of this controller without any runtime data */
    ProcessController newInstance() {
        return new ProcessController(instanceId, instanceTag, processConfig, processDir);
    }

    /** TESTING only: Returns the future that is scheduled to recover a crashed application */
    Future<?> getRecoverTask() {
        return recoverTask;
    }

    /**
     * DTO written to the file-system after launching a process.
     */
    private static class ProcessControllerDto {

        /** The native process identifier */
        public long pid;

        /**
         * Timestamp when the process was launched. OS dependent format, not meant for user display, no guarantee about format
         * (relative, absolute, ...)
         */
        public long startTime;

    }

}
