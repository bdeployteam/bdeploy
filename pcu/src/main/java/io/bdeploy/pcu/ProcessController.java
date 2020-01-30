package io.bdeploy.pcu;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessHandle.Info;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.pcu.util.Formatter;

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

    private final MdcLogger logger = new MdcLogger(ProcessController.class);

    /** File that holds the standard out as well as standard error written by the process */
    public static final String OUT_TXT = "out.txt";
    private static final String OUT_TMP = "out.tmp";

    /** Name of the file stored on-disk. Holds information to re-attach to the process */
    private static final String JSON_FILE = "app.json";

    private final Path processDir;
    private final String instanceUid;
    private final String instanceTag;
    private final ProcessConfiguration processConfig;

    /** Lock that is used to guard start, stop and termination */
    private final ReentrantLock lock = new ReentrantLock();

    /** The active task that has the lock */
    private String lockTask = null;

    /** Executor used to schedule re-launching of application */
    private ScheduledExecutorService executorService;

    /** Task scheduled to monitor the up-time */
    private Future<?> uptimeTask;

    /** Task scheduled to start the application when crashed */
    private Future<?> recoverTask;

    /** The native process. Null if not running */
    private ProcessHandle process;

    /** Future to test for process termination */
    private CompletableFuture<ProcessHandle> processExit;

    /** The current status of the process */
    private ProcessState processState = ProcessState.STOPPED;

    /** Flag indicating whether or not the process is killed intentionally */
    private boolean stopRequested = false;

    /** Flag indicating that stop command gave up */
    private boolean stopRequestGaveUp = false;

    /** Time when the process has been started */
    private Instant startTime;

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
    private final List<Consumer<ProcessState>> statusListeners = new ArrayList<>();

    /** Replace variables used in the start/stop command and it's arguments */
    private VariableResolver variableResolver;

    /**
     * Creates a new process controller for the given configuration.
     *
     * @param instanceUid
     *            unique identifier of the parent instance
     * @param instanceTag
     *            unique identifier of the parent instance version
     * @param pc
     *            the configuration of the process to launch
     * @param processDir
     *            the "runtime" directory, used for data specific to this
     *            launch
     */
    public ProcessController(String instanceUid, String instanceTag, ProcessConfiguration pc, Path processDir) {
        this.logger.setMdcValue(instanceUid, instanceTag, pc.uid);
        this.instanceUid = instanceUid;
        this.instanceTag = instanceTag;
        this.processConfig = pc;
        this.recoverAttempts = pc.processControl.noOfRetries;
        this.processDir = processDir;
    }

    @Override
    public String toString() {
        ProcessStatusDto status = getStatus();
        List<String> logs = new ArrayList<>();
        logs.addAll(status.logStatusDetails());
        logs.add("Alive: " + (process != null ? process.isAlive() : Boolean.FALSE.toString()));
        if (process != null) {
            logs.add("Children: " + process.children().count());
        }
        logs.add("Directory: " + processDir);
        logs.add("Active Task: " + (lockTask != null ? lockTask : "-"));
        logs.add("Stop Requested: " + stopRequested);
        logs.add("Stop Request Failed: " + stopRequestGaveUp);
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
    public void addStatusListener(Consumer<ProcessState> listener) {
        this.statusListeners.add(listener);
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
     * Returns a DTO containing information about the process.
     */
    public ProcessStatusDto getStatus() {
        ProcessStatusDto dto = new ProcessStatusDto();
        dto.processState = processState;
        dto.appUid = processConfig.uid;
        dto.appName = processConfig.name;
        dto.instanceTag = instanceTag;
        dto.instanceUid = instanceUid;

        // Collect process information if running
        if (processState.isRunning()) {
            dto.processDetails = collectProcessInfo(process);
        }

        // Retry attempts when process crashed
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

        return dto;
    }

    /** Recursively collects runtime information about the given process */
    private ProcessDetailDto collectProcessInfo(ProcessHandle process) {
        ProcessDetailDto dto = new ProcessDetailDto();
        dto.pid = process.pid();

        // Collect process info
        Info info = process.info();
        if (info.startInstant().isPresent()) {
            dto.startTime = info.startInstant().get().toEpochMilli();
        }
        if (info.totalCpuDuration().isPresent()) {
            dto.totalCpuDuration = info.totalCpuDuration().get().getSeconds();
        }
        dto.command = info.command().orElse(null);
        dto.arguments = info.arguments().orElse(null);
        dto.user = info.user().orElse(null);

        // Collect child info
        process.children().forEach(c -> dto.children.add(collectProcessInfo(c)));
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
     */
    public void start() {
        executeLocked("Start", false, () -> doStart(true));
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
     */
    public void stop() {
        executeLocked("Stop", false, this::doStop);
    }

    /**
     * Tries to determine whether or not a process for this application is still running. This is done
     * by reading the JSON file written when the application was launched. Status is changed to RUNNING
     * if recovering was successfully. Status remains STOPPED if the process is not alive any more.
     */
    public void recover() {
        executeLocked("Recover", false, this::doRecover);
    }

    /**
     * Keeps the application running in the current state and discards
     * all information that this controller has. Only useful in UNIT tests.
     */
    public void detach() {
        executeLocked("Detach", false, this::doDetach);
    }

    /** Starts the application */
    private void doStart(boolean resetRecoverCount) {
        if (processState == ProcessState.RUNNING || processState == ProcessState.RUNNING_UNSTABLE) {
            throw new PcuRuntimeException("Application is already running.");
        }
        logger.log(l -> l.info("Starting application."));

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
        PathHelper.deleteRecursive(infoFile);

        // Cancel any pending restart tasks
        stopTime = null;
        stopRequested = false;
        if (recoverTask != null) {
            recoverTask.cancel(false);
        }

        try {
            process = launch(processConfig.start).toHandle();
            processExit = process.onExit();
            startTime = process.info().startInstant().orElseGet(() -> {
                logger.log(l -> l.error("Start time of process not available, falling back to current time. PID = {}.",
                        process.pid()));
                return Instant.now();
            });

            // Persist the process that we just started to recover it if required
            ProcessControllerDto dto = new ProcessControllerDto();
            dto.pid = process.pid();
            dto.startTime = internalGetProcessStartTimestampCorrected(dto.pid, startTime);
            Files.write(infoFile, StorageHelper.toRawBytes(dto));
            logger.log(l -> l.info("Successfully started application. PID = {}.", dto.pid));

            // Attach exit handle to get notified about termination
            monitorProcess();
        } catch (IOException e) {
            logger.log(l -> l.error("Failed to launch application.", e));
            processState = ProcessState.CRASHED_PERMANENTLY;
            cleanup();
        }
    }

    private long internalGetProcessStartTimestampCorrected(long pid, Instant reportedStartTime) {
        if (OsHelper.getRunningOs() == OperatingSystem.LINUX) {
            // see bug DCS-546 - the JVM on linux uses an invalid formula to calculate the absolute timestamp at which a process was started.
            // There are various dynamically updated offsets and timestamps in play, thus the value may change over time. There /is/ a stable
            // value we can use, and actually what we do here is the very same thing as the JVM does when reading the start time. The read value
            // is not absolute though, but relative to the kernel boot time (which is sufficient for what we want). We just omit addition
            // of real time and boot time offsets as done in the linux kernel when querying the boottime of the kernel.
            try {
                // read the single line from /proc/[pid]/stat, field no 22 is the start time.
                String line = new String(Files.readAllBytes(Paths.get("/proc", String.valueOf(pid), "stat")),
                        StandardCharsets.UTF_8);
                String[] split = line.split(" ");
                return Long.parseLong(split[21]);
            } catch (Exception e) {
                logger.log(l -> l.warn("Cannot read corrected start time of process, PID = {}.", pid, e));
            }
        }

        // we (for now) trust the OS to deliver a stable absolute timestamp.
        return reportedStartTime.toEpochMilli();
    }

    /** Stops the application */
    private void doStop() {
        if (processState == ProcessState.STOPPED) {
            throw new PcuRuntimeException("Application is already stopped.");
        }

        // Set flag that the termination of the process is expected
        stopRequested = true;
        stopRequestGaveUp = false;
        if (processState == ProcessState.CRASHED_WAITING) {
            logger.log(l -> l.info("Aborting restart attempt of application."));
            processState = ProcessState.STOPPED;
            cleanup();
            return;
        }
        long pid = process.pid();
        logger.log(l -> l.info("Stopping application. PID = {}", pid));

        // try to gracefully stop the process using it's stop command
        doInvokeStopCommand(processConfig.stop);
        if (processExit == null || processExit.isDone()) {
            afterTerminated();
            return;
        }

        // No stop command defined. Go on with destroying the process via its handle
        Instant stopRequestedAt = Instant.now();
        boolean stopped = doDestroyProcess();

        // Inform about the result of the stop operation
        if (stopped) {
            afterTerminated();
            Duration duration = Duration.between(stopRequestedAt, Instant.now());
            logger.log(l -> l.info("Application is now stopped. Stopping took {}", Formatter.formatDuration(duration)));
        } else {
            stopRequestGaveUp = true;
            logger.log(l -> l.error("Giving up to terminate application. Process does not respond to kill requests."));
            logger.log(l -> l.error("Application state remains {} ", processState));
        }
    }

    /** Recovers the application when running */
    private void doRecover() {
        Path pidFile = processDir.resolve(JSON_FILE);
        if (!Files.exists(pidFile)) {
            return;
        }

        // Acquire a process handle for the stored PID
        ProcessControllerDto dto = StorageHelper.fromPath(pidFile, ProcessControllerDto.class);
        Optional<ProcessHandle> ph = ProcessHandle.of(dto.pid);
        if (!ph.isPresent()) {
            PathHelper.deleteRecursive(pidFile);
            return;
        }

        // Remember handle and exit hook
        process = ph.get();
        processExit = process.onExit();

        // make sure that this is still the process we're looking for.
        startTime = process.info().startInstant().orElseGet(() -> {
            logger.log(l -> l.warn("Recovering process handle does not have a start time! PID = {}", dto.pid));
            return Instant.now();
        });

        long correctedStart = internalGetProcessStartTimestampCorrected(dto.pid, startTime);
        if (Long.compare(correctedStart, dto.startTime) == 0) {
            logger.log(l -> l.info("Successfully attached to application. PID = {}.", dto.pid));
            monitorProcess();
        } else {
            logger.log(l -> l.info("Discarding existing process information due to start time mismatch. {} != {}, PID = {}.",
                    startTime, dto.startTime, dto.pid));
            PathHelper.deleteRecursive(pidFile);
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
        process = null;

        // Cancel the hook that is notified when the process terminates
        if (processExit != null) {
            processExit.cancel(false);
            processExit = null;
        }

        // Cancel restart task and monitoring task
        shutdownExecutor();
    }

    /**
     * Tries to acquire the lock in order to execute the given runnable. Fails immediately if the lock is held by another thread.
     * Notifies all listeners if the process state changes after the runnable has been invoked.
     * <p>
     * <b>Implementation note:</b> Only a single process-state change should be performed within a given task execution.
     * State listeners are notified about about changes when the task completes. Thus when a task performs multiple state changes
     * then the listeners are only notified about the final state. In all other cases listeners must be notified manually.
     * </p>
     */
    private void executeLocked(String taskName, boolean wait, Runnable runnable) {
        // Fail fast if another thread holds the lock
        if (!wait && !lock.tryLock()) {
            throw new PcuRuntimeException("Cannot execute '" + taskName + "' task. Task '" + lockTask + "' is in progress.");
        }

        // Wait until we get the lock
        try {
            if (wait && !lock.tryLock()) {
                logger.log(l -> l.info("Task '{}' is waiting for operation '{}' to finish.", taskName, lockTask));
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
            throw new PcuRuntimeException("Failed to execute task '" + taskName + "'", ex);
        } finally {
            lockTask = null;
            lock.unlock();

            // Notify listeners when state changes
            if (processState != oldState) {
                notifyListeners();
            }
        }
    }

    /**
     * Waits until the process releases the lock that is hold on the out.txt file.
     */
    private void waitForLockRelease() {
        // Only Windows is holding file-locks
        if (OsHelper.getRunningOs() != OperatingSystem.WINDOWS) {
            return;
        }

        // We try for some time to rename the file. If that succeeds
        // the lock held by the process is gone.
        File tmpFile = processDir.resolve(OUT_TMP).toFile();
        File outFile = processDir.resolve(OUT_TXT).toFile();

        // Lock is typically held for up to 150ms
        int retry = 1;
        while (!outFile.renameTo(tmpFile) && retry <= 20) {
            logger.log(l -> l.info("Waiting for file-lock to be released"));
            doSleep(50, TimeUnit.MILLISECONDS);
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
        // Notify when the status changes
        processExit.thenRunAsync(() -> executeLocked("ExitHook", true, this::onTerminated));

        // Set to running if launched from stopped or crashed
        if (processState == ProcessState.STOPPED || processState == ProcessState.CRASHED_PERMANENTLY) {
            processState = ProcessState.RUNNING;
            logger.log(l -> l.info("Application status is now RUNNING."));
        }

        // Schedule uptime monitor if launched from crashed waiting
        if (processState == ProcessState.CRASHED_WAITING) {
            processState = ProcessState.RUNNING_UNSTABLE;
            if (recoverCount > 0) {
                logger.log(l -> l.info("Application successfully recovered. Attempts: {}/{}", recoverCount, recoverAttempts));
            }
            logger.log(l -> l.info("Application status is now RUNNING_UNSTABLE."));

            // Periodically watch if the process remains alive
            long rateInSeconds = stableThreshold.dividedBy(10).getSeconds();
            if (rateInSeconds == 0) {
                rateInSeconds = 1;
            }
            uptimeTask = executorService.scheduleAtFixedRate(this::doCheckUptime, rateInSeconds, rateInSeconds, TimeUnit.SECONDS);
            String requiredUptime = Formatter.formatDuration(stableThreshold);
            logger.log(l -> l.info("Application will be marked as stable after: {}", requiredUptime));
        }
    }

    /** Callback method that is executed when the process terminates */
    private void onTerminated() {
        try {
            // Process terminated after stop request. Nothing to do
            if (stopRequested && !stopRequestGaveUp) {
                return;
            }
            afterTerminated();
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to execute termination hook", ex));
        }
    }

    /** Callback method that is executed after the process terminates */
    private void afterTerminated() {
        stopTime = Instant.now();
        Duration uptime = Duration.between(startTime, stopTime);
        String uptimeString = Formatter.formatDuration(uptime);

        // Someone requested termination
        if (stopRequested) {
            logger.log(l -> l.info("Application terminated after stop request. Total uptime: {}", uptimeString));
            processState = ProcessState.STOPPED;
            waitForLockRelease();
            cleanup();
            return;
        }

        // One-Shot process that can terminate at any time
        if (!processConfig.processControl.keepAlive) {
            String message = "Application terminated. Remains stopped as keep-alive is not configured. Total uptime: {}";
            logger.log(l -> l.info(message, uptimeString));
            processState = ProcessState.STOPPED;
            waitForLockRelease();
            cleanup();
            return;
        }

        // Process terminated unexpectedly
        logger.log(l -> l.error("Application terminated unexpectedly. Total uptime: {}", uptimeString));

        // Give up when the application keeps crashing
        if (recoverAttempts > 0 && recoverCount >= recoverAttempts) {
            String message = "Giving up to launch application. Tried {} times to launch without success.";
            logger.log(l -> l.error(message, recoverCount));
            processState = ProcessState.CRASHED_PERMANENTLY;
            waitForLockRelease();
            cleanup();
            return;
        }
        Duration delay = recoverDelays[Math.min(recoverCount, recoverDelays.length - 1)];
        processState = ProcessState.CRASHED_WAITING;
        recoverCount++;

        // Schedule restarting of application
        if (executorService == null) {
            executorService = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setDaemon(true).setNameFormat(processConfig.uid).build());
        }

        // Schedule restarting of application based on configured delay
        Runnable task = () -> executeLocked("Restart", true, this::doRestart);
        if (delay.isZero()) {
            logger.log(l -> l.info("Re-launching application immediatly."));
            recoverTask = executorService.schedule(task, 0, TimeUnit.SECONDS);
        } else {
            logger.log(l -> l.info("Waiting {} before re-launching application.", Formatter.formatDuration(delay)));
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
        } else if (processState == ProcessState.RUNNING || processState == ProcessState.RUNNING_UNSTABLE) {
            logger.log(l -> l.info("Application has been started while in crash-back-off. Doing nothing."));
            return;
        }

        // Start the application again
        doStart(false);
    }

    /** Switches the state of a process to running if it is alive for a given time period */
    private void doCheckUptime() {
        executeLocked("CheckUptime", true, () -> {
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
            shutdownExecutor();

            // Reset counter and set application to stable
            recoverCount = 0;
            processState = ProcessState.RUNNING;
            logger.log(l -> l.info("Uptime threshold reached. Marking as stable again."));
            logger.log(l -> l.info("Application status is now RUNNING."));
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
    private Process launch(List<String> cmd) throws IOException {
        List<String> command = replaceVariables(cmd);
        logger.log(l -> l.debug("Launching new process {}", command));

        ProcessBuilder b = new ProcessBuilder(command).directory(processDir.toFile());
        b.redirectErrorStream(true);
        b.redirectOutput(processDir.resolve(OUT_TXT).toFile());
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
    private void doInvokeStopCommand(List<String> stopCommand) {
        if (stopCommand == null || stopCommand.isEmpty()) {
            logger.log(l -> l.debug("No stop command configured."));
            return;
        }
        try {
            logger.log(l -> l.info("Invoking configured stop command.", stopCommand));
            Process p = launch(stopCommand);
            boolean exited = p.waitFor(processConfig.processControl.gracePeriod, TimeUnit.MILLISECONDS);
            if (!exited) {
                // stop command did not complete within allowed time, kill both
                logger.log(l -> l.warn("Stop command did not finish within configured grace period."));
                p.destroy();
                boolean stopExited = p.waitFor(200, TimeUnit.MILLISECONDS);
                if (!stopExited) {
                    logger.log(l -> l.warn("Stop command refuses to exit, killing."));
                    p.destroyForcibly();
                }
            } else {
                // stop command completed within the timeout, check status
                int exitValue = p.exitValue();
                if (exitValue != 0) {
                    logger.log(l -> l.warn("Stop command exited with non-zero code: {} ", exitValue));
                } else {
                    logger.log(l -> l.info("Stop command exited with return code 0 "));
                }
            }
        } catch (Exception e) {
            logger.log(l -> l.error("Failed to execute stop command.", e));
        }
    }

    /** Signals the process to terminate and waits for the completion */
    private boolean doDestroyProcess() {
        int retryCount = 0;
        boolean stopped = false;
        while (!stopped && retryCount < 10) {
            // Destroy or forcibly destroy based on the retry counter
            if (retryCount == 0) {
                destroy(process);
            } else {
                int logCount = retryCount;
                logger.log(l -> l.warn("Attempt {}: Kill application.", logCount));
                destroyForcibly(process);
            }

            // Wait configured delay to stop
            try {
                processExit.get(processConfig.processControl.gracePeriod, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.log(l -> l.warn("Timed-out waiting for application to exit. Timeout: {} ms",
                        processConfig.processControl.gracePeriod));
            } catch (Exception e) {
                logger.log(l -> l.warn("Exception while waiting for application to terminate.", e));
            }

            // Continue when process is still alive.
            stopped = processExit == null || processExit.isDone();
            if (!stopped) {
                retryCount++;
            }
        }
        return stopped;
    }

    /** Closes the executor so that the resources are released */
    private void shutdownExecutor() {
        if (recoverTask != null) {
            recoverTask.cancel(true);
            recoverTask = null;
        }
        if (uptimeTask != null) {
            uptimeTask.cancel(true);
            uptimeTask = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    /** Notifies all listeners about the of the process */
    private void notifyListeners() {
        try {
            statusListeners.forEach(c -> c.accept(processState));
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to notify listener about current process status.", ex));
        }
    }

    /** Returns a new-instance of this controller without any runtime data */
    ProcessController newInstance() {
        return new ProcessController(instanceUid, instanceTag, processConfig, processDir);
    }

    /** Returns the future that is scheduled to recover a crashed application */
    Future<?> getRecoverTask() {
        return recoverTask;
    }

    /** Sleeps the given number of seconds */
    private static boolean doSleep(long value, TimeUnit unit) {
        try {
            unit.sleep(value);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
