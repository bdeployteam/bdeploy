package io.bdeploy.pcu;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public abstract class AbstractBulkControl implements BulkControlStrategy {

    private final MdcLogger logger = new MdcLogger(SequentialBulkControl.class);

    protected final String user;
    protected final String activeTag;
    protected final ProcessControlGroupConfiguration controlGroup;
    protected final ProcessList processes;

    protected AbstractBulkControl(String user, String instance, String tag, ProcessControlGroupConfiguration group,
            ProcessList processes) {
        this.user = user;
        this.activeTag = tag;
        this.controlGroup = group;
        this.processes = processes;
        logger.setMdcValue(instance);
    }

    /**
     * Synchronously starts a given process, potentially including wait for startup probe, etc.
     *
     * @param appId the application to start.
     * @return whether the application was started given its configuration (e.g. including awaiting startup probe).
     */
    protected boolean doStartSingle(String appId) {
        ProcessController controller = processes.get(appId);
        if (controller == null) {
            // this is silently ignored in the sequential mode. we consider it "OK" here to achieve the same.
            return true;
        }

        // if the process is already running, we'll use that.
        if (controller.getState().isRunning()) {
            return true;
        }

        // if the process is not *planned* to start, we reject it. this is important in case
        // we use startAll and then stopAll to "abort" starting.
        if (controller.getState() != ProcessState.STOPPED_START_PLANNED) {
            logger.log(l -> l.warn("Skipping start of {}, not in planned start state", controller.getDescriptor().id));
            return false;
        }

        // Start it
        ProcessControlGroupWaitType waitType = controlGroup.startWait;
        if (waitType == ProcessControlGroupWaitType.CONTINUE) {
            controller.start(user);
            return true;
        }

        try (MultiStateListener listener = MultiStateListener.createFor(controller)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            ProcessState happyState;
            switch (waitType) {
                case WAIT:
                    happyState = ProcessState.RUNNING;
                    listener.on(ProcessState.STOPPED,
                            () -> future.completeExceptionally(new IllegalStateException("Stopped while starting")));
                    listener.on(ProcessState.RUNNING_STOP_PLANNED,
                            () -> future.completeExceptionally(new IllegalStateException("Stop planned while starting")));
                    break;
                case WAIT_UNTIL_STOPPED:
                    happyState = ProcessState.STOPPED;
                    break;
                default:
                    throw new IllegalStateException("Missing implementation of "
                            + ProcessControlGroupWaitType.class.getSimpleName() + ' ' + waitType.name());
            }

            listener.on(happyState, () -> future.complete(Boolean.TRUE));
            listener.on(ProcessState.CRASHED_PERMANENTLY,
                    () -> future.completeExceptionally(new IllegalStateException("Crashed permanently")));

            controller.start(user);

            if (controller.getState() == happyState) {
                return true;
            }

            logger.log(l -> l.info("Delaying startup of next process until current process is in state {}", happyState.name()),
                    activeTag, appId);

            try {
                return future.get(20, TimeUnit.MINUTES); // await process changes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (TimeoutException e) {
                logger.log(l -> l.error("Timed out starting {}", controller.getDescriptor().id));
                return false;
            } catch (ExecutionException e) {
                logger.log(l -> l.warn("Failed to start {}: {}", controller.getDescriptor().id, e.getCause().toString()));
                return false;
            }
        }
    }

    /**
     * Synchronously stops a process, waiting until the process is gone.
     *
     * @param process the process to stop.
     * @return whether the process was stopped.
     */
    protected boolean doStopSingle(ProcessController process) {
        try {
            logger.log(l -> l.debug("Stopping single application {}", process.getDescriptor().id));
            process.stop(user);
            return true;
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to stop application.", ex), process.getStatus().instanceTag,
                    process.getDescriptor().id);
            return false;
        }
    }
}
