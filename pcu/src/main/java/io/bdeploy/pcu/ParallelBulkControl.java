package io.bdeploy.pcu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;

/**
 * Implementation of {@link BulkControlStrategy} which <strong>triggers</strong> process operations in parallel for a single
 * control groups.
 * <p>
 * The group operations still wait for all parallel operations to complete before proceeding.
 */
public class ParallelBulkControl extends AbstractBulkControl {

    final MdcLogger logger = new MdcLogger(ParallelBulkControl.class);

    private final ExecutorService parallelExec;

    public ParallelBulkControl(String user, String instance, String tag, ProcessControlGroupConfiguration group,
            ProcessList processes) {
        super(user, instance, tag, group, processes);

        logger.setMdcValue(instance);
        parallelExec = Executors.newCachedThreadPool(r -> new Thread(r, "Parallel Bulk / " + instance));
    }

    @Override
    public List<String> startGroup(Map<String, ProcessController> running) {
        List<Future<TaskResult>> tasks = new ArrayList<>();
        for (String appId : controlGroup.processOrder) {
            tasks.add(parallelExec.submit(() -> new TaskResult(appId, doStartSingle(running, appId))));
        }

        logger.log(l -> l.info("All applications of control group {} processed.", controlGroup.name));

        List<String> failed = new ArrayList<>();
        for (Future<TaskResult> task : tasks) {
            try {
                var r = task.get();
                if (!r.result) {
                    failed.add(r.appId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(l -> l.warn("Parallel startup interrupted"));
            } catch (Exception e) {
                logger.log(l -> l.warn("Error when reading result of parallel start operation", e));
            }
        }

        return failed;
    }

    @Override
    public List<String> stopGroup(Collection<ProcessController> toStop) {
        List<Future<TaskResult>> tasks = new ArrayList<>();

        // Execute shutdown in new thread so that the caller is not blocked
        Instant start = Instant.now();
        for (ProcessController process : toStop) {
            tasks.add(parallelExec.submit(() -> new TaskResult(process.getDescriptor().uid, doStopSingle(process))));
        }

        List<String> stopped = new ArrayList<>();
        for (Future<TaskResult> task : tasks) {
            try {
                var r = task.get();
                if (r.result) {
                    stopped.add(r.appId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(l -> l.warn("Parallel stop interrupted"));
            } catch (Exception e) {
                logger.log(l -> l.warn("Error when reading result of parallel stop operation", e));
            }
        }

        // Check if we could stop all applications
        Duration duration = Duration.between(start, Instant.now());
        boolean allStopped = toStop.stream().map(pc -> stopped.contains(pc.getDescriptor().uid)).noneMatch(b -> b == false);
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
        this.parallelExec.shutdownNow();
    }

    private static final class TaskResult {

        public final String appId;
        public final boolean result; // use-case dependent.

        public TaskResult(String appId, boolean result) {
            this.appId = appId;
            this.result = result;

        }
    }

}
