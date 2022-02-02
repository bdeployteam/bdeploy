package io.bdeploy.pcu;

import java.time.Duration;
import java.util.Map;

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
        try {
            if (controlGroup.startWait == ProcessControlGroupWaitType.WAIT) {
                try (StateListener listener = StateListener.createFor(controller).expect(ProcessState.RUNNING)) {
                    controller.start(user);

                    if (controller.getState() != ProcessState.RUNNING) {
                        logger.log(l -> l.info("Waiting for startup"), activeTag, appId);

                        // Should this be configurable somehow? If an application does not start correctly, this is a bug in the application.
                        listener.await(Duration.ofHours(1));
                    }
                }
            } else {
                controller.start(user);
            }
            return true;
        } catch (Exception ex) {
            logger.log(l -> l.info("Failed to start application.", ex), activeTag, appId);
            return false;
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