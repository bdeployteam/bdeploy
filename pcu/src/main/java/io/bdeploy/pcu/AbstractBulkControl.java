package io.bdeploy.pcu;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

public abstract class AbstractBulkControl implements BulkControlStrategy {

    private final MdcLogger logger = new MdcLogger(SequentialBulkControl.class);

    protected final String user;
    protected final String activeTag;
    protected final ProcessControlGroupConfiguration controlGroup;
    protected final ProcessList processes;

    public AbstractBulkControl(String user, String instance, String tag, ProcessControlGroupConfiguration group,
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
     * @param running all currently running applications - state is from before bulk-operation.
     * @param appId the application to start.
     * @return whether the application was started given its configuration (e.g. including awaiting startup probe).
     */
    protected boolean doStartSingle(Map<String, ProcessController> running, String appId) {
        ProcessController controller = processes.get(appId);
        if (controller == null) {
            // this is silently ignored in the sequential mode. we consider it "OK" here to achieve the same.
            return true;
        }

        // Write logs when the application is already running
        if (running.containsKey(appId)) {
            ProcessStatusDto data = controller.getStatus();
            if (data.instanceTag.equals(activeTag)) {
                logger.log(l -> l.warn("Application already running in a different version."), data.instanceTag, data.appUid);
            } else {
                logger.log(l -> l.info("Application already running."), data.instanceTag, data.appUid);
            }
            return true;
        }

        // Only start when auto-start is configured
        ProcessConfiguration config = controller.getDescriptor();
        if (config.processControl.startType != ApplicationStartType.INSTANCE) {
            logger.log(l -> l.info("Application does not have 'instance' start type set."), activeTag, appId);
            return true;
        }

        // Start it
        if (controlGroup.startWait == ProcessControlGroupWaitType.WAIT) {
            try (MultiStateListener listener = MultiStateListener.createFor(controller)) {
                CompletableFuture<Boolean> processStarted = new CompletableFuture<>();

                // RUNNING is OK, CRASHED_PERMANENTLY and STOPPED are signals to give up immediately.
                listener.on(ProcessState.RUNNING, () -> processStarted.complete(true));
                listener.on(ProcessState.CRASHED_PERMANENTLY,
                        () -> processStarted.completeExceptionally(new RuntimeException("Permanent crash while starting")));
                listener.on(ProcessState.STOPPED,
                        () -> processStarted.completeExceptionally(new RuntimeException("Stopped while starting")));

                controller.start(user);

                if (controller.getState() != ProcessState.RUNNING) {
                    logger.log(l -> l.info("Waiting for startup"), activeTag, appId);
                    try {
                        return processStarted.get(); // await process changes.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (ExecutionException e) {
                        logger.log(l -> l.warn("Failed to start " + config.uid + ": " + e.getCause().toString()));
                        return false;
                    }
                }

                return true;
            }
        } else {
            controller.start(user);
            return true;
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
            process.stop(user);
            return true;
        } catch (Exception ex) {
            logger.log(l -> l.error("Failed to stop application.", ex), process.getStatus().instanceTag,
                    process.getDescriptor().uid);
            return false;
        }
    }

}