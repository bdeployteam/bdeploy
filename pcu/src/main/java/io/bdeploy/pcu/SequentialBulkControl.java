package io.bdeploy.pcu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;

/**
 * Sequential implementation of {@link BulkControlStrategy}. This implementation performs one operation after another and waits
 * for operations to complete before triggering the next operation.
 */
public class SequentialBulkControl extends AbstractBulkControl {

    private final MdcLogger logger = new MdcLogger(SequentialBulkControl.class);

    public SequentialBulkControl(String user, String instance, String tag, ProcessControlGroupConfiguration group,
            ProcessList processes) {
        super(user, instance, tag, group, processes);
        logger.setMdcValue(instance);
    }

    @Override
    public List<String> startGroup(Collection<String> toStart) {
        List<String> failed = new ArrayList<>();
        for (String appId : toStart.stream()
                .sorted((a, b) -> controlGroup.processOrder.indexOf(a) - controlGroup.processOrder.indexOf(b)).toList()) {
            if (!doStartSingle(appId)) {
                failed.add(appId);
            }
        }
        logger.log(l -> l.info("All applications of control group {} processed", controlGroup.name));

        return failed;
    }

    @Override
    public List<String> stopGroup(Collection<ProcessController> toStop) {
        List<String> stopped = new ArrayList<>();

        // Execute shutdown in new thread so that the caller is not blocked
        Instant start = Instant.now();
        for (ProcessController process : toStop.stream().sorted((a, b) -> controlGroup.processOrder.indexOf(b.getDescriptor().uid)
                - controlGroup.processOrder.indexOf(a.getDescriptor().uid)).toList()) {
            if (doStopSingle(process)) {
                stopped.add(process.getDescriptor().uid);
            }
        }

        // Check if we could stop all applications
        Duration duration = Duration.between(start, Instant.now());
        boolean allStopped = toStop.stream().map(pc -> stopped.contains(pc.getDescriptor().uid)).noneMatch(b -> !b);
        if (allStopped) {
            logger.log(l -> l.info("Applications in Control Group {} have been stopped in {} ", controlGroup.name,
                    ProcessControllerHelper.formatDuration(duration)));
        } else {
            String stillRunning = toStop.stream().map(pc -> pc.getDescriptor().name).collect(Collectors.joining(","));
            logger.log(l -> l.warn("Not all applications in Control Group {} could be stopped.", controlGroup.name));
            logger.log(l -> l.warn("Following applications are still running or in an undefined state: {}", stillRunning));
        }

        return stopped;
    }

    @Override
    public void close() {
        // nothing.
    }

}
