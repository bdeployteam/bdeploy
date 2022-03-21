package io.bdeploy.pcu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Handling of "Start All" and "Stop All" process controls.
 */
public class BulkProcessController {

    private final MdcLogger logger = new MdcLogger(BulkProcessController.class);
    private final ProcessList processes;
    private final String activeTag;
    private final String instanceUuid;

    public BulkProcessController(String instanceUuid, String activeTag, ProcessList processes) {
        this.instanceUuid = instanceUuid;
        this.processes = processes;
        this.activeTag = activeTag;
        this.logger.setMdcValue(instanceUuid);
    }

    /**
     * Start all processes from the own {@link ProcessList} with the INSTANCE start type.
     *
     * @param user the requesting user.
     * @param running the already (potentially from other tags) running processes.
     */
    public void startAll(String user, Map<String, ProcessController> running, Collection<String> applicationIds) {
        Instant start = Instant.now();

        List<String> leftOverApps = new ArrayList<>(applicationIds);
        List<ProcessControlGroupConfiguration> groups = new ArrayList<>(processes.getControlGroups());

        for (var appId : applicationIds) {
            if (running.containsKey(appId)) {
                logger.log(l -> l.info("Application already running from version {}: {}",
                        running.get(appId).getStatus().instanceTag, appId));
            }

            var controller = processes.controllers.get(appId);
            if (controller.getState().isStopped() && !running.containsKey(appId)) {
                // process will be started in the group at some point. mark it as such.
                controller.prepareStart(user);
            }

            // find a control group for the process, if none is found, it is left over.
            Optional<ProcessControlGroupConfiguration> grp = groups.stream().filter(g -> g.processOrder.contains(appId))
                    .findAny();
            if (grp.isPresent()) {
                leftOverApps.remove(appId);
            }
        }

        // find processes which don't belong to any group and assign them to an anonymous one.
        if (!leftOverApps.isEmpty()) {
            ProcessControlGroupConfiguration anonGroup = new ProcessControlGroupConfiguration();
            anonGroup.name = "Unassigned Processes";
            anonGroup.processOrder.addAll(leftOverApps); // no order.
            groups.add(anonGroup);
        }

        boolean failing = false;
        for (ProcessControlGroupConfiguration controlGroup : groups) {
            if (!failing) {
                List<String> appsInGroup = applicationIds.stream()
                        .filter(a -> controlGroup.processOrder.contains(a) && !running.containsKey(a)).toList();
                if (appsInGroup.isEmpty()) {
                    continue;
                }

                try (BulkControlStrategy bulk = BulkControlStrategy.create(user, instanceUuid, activeTag, controlGroup, processes,
                        controlGroup.startType)) {
                    List<String> failed = bulk.startGroup(appsInGroup);

                    if (!failed.isEmpty()) {
                        logger.log(l -> l.warn("Not all applications could be started, skipping other Control Groups. Failed {}",
                                failed));
                        failing = true;
                    }
                }
            } else {
                // a PREVIOUS group failed. Instead of starting we revert to STOPPED state in case we prepared starting.
                for (var appId : applicationIds) {
                    var cfg = processes.controllers.get(appId);
                    if (controlGroup.processOrder.contains(appId) && cfg.getState() == ProcessState.STOPPED_START_PLANNED) {
                        cfg.abortStart(user);
                    }
                }
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        if (!failing) {
            logger.log(l -> l.info("Applications have been started in {}", ProcessControllerHelper.formatDuration(duration)),
                    activeTag);
            return;
        }

    }

    /**
     * Stop all processes of the instance, based on all running applications, *regardless* of their origin tag.
     * <p>
     * The current control group configuration (from the active tag) is used to order process stops. Processes which
     * are no longer part of the instance configuration are stopped last.
     *
     * @param user the requesting user.
     * @param running all currently running processes in the instance.
     */
    public void stopAll(String user, Map<String, ProcessController> running, Collection<String> applicationIds) {
        // use the CURRENT group configuration where possible.
        List<ProcessControlGroupConfiguration> currentGroups = processes.getControlGroups();

        // reverse the groups.
        List<ProcessControlGroupConfiguration> reverseGroups = new ArrayList<>(currentGroups);
        Collections.reverse(reverseGroups);

        List<String> leftOverApps = new ArrayList<>(applicationIds);

        // Set intent that all should be stopped - the order does not matter as all of them are set to scheduled stop IMMEDIATELY.
        for (ProcessControlGroupConfiguration controlGroup : reverseGroups) {
            // reverse ordered by the current control groups order.
            List<ProcessController> toStop = getToStop(running, applicationIds, controlGroup);

            for (var process : toStop) {
                try {
                    if (process.getState() == ProcessState.STOPPED_START_PLANNED) {
                        // abort start.
                        process.abortStart(user);
                    } else {
                        process.prepareStop(user);
                    }
                    leftOverApps.remove(process.getDescriptor().uid);
                } catch (Exception ex) {
                    String appId = process.getDescriptor().uid;
                    String tag = process.getStatus().instanceTag;
                    logger.log(l -> l.error("Failed to prepare stopping of application.", ex), tag, appId);
                }
            }
        }

        // processes which are not assigned to any group in the CURRENT instance version - stopped last (different to previous BDeploy versions).
        if (!leftOverApps.isEmpty()) {
            ProcessControlGroupConfiguration anonGroup = new ProcessControlGroupConfiguration();
            anonGroup.name = "Unassigned Processes";
            anonGroup.processOrder.addAll(leftOverApps); // no order.
            reverseGroups.add(anonGroup);
        }

        for (ProcessControlGroupConfiguration controlGroup : reverseGroups) {
            // reverse ordered by the current control groups order.
            List<ProcessController> toStop = getToStop(running, applicationIds, controlGroup);

            if (toStop.isEmpty()) {
                continue;
            }

            try (BulkControlStrategy bulk = BulkControlStrategy.create(user, instanceUuid, activeTag, controlGroup, processes,
                    controlGroup.stopType)) {
                bulk.stopGroup(toStop);
            }
        }
    }

    private List<ProcessController> getToStop(Map<String, ProcessController> running, Collection<String> applicationIds,
            ProcessControlGroupConfiguration controlGroup) {
        List<ProcessController> toStop = applicationIds.stream().filter(e -> controlGroup.processOrder.contains(e))
                .sorted((a, b) -> Integer.compare(controlGroup.processOrder.indexOf(b), controlGroup.processOrder.indexOf(a)))
                .map(e -> {
                    if (running.containsKey(e)) {
                        return running.get(e);
                    } else {
                        return processes.controllers.get(e);
                    }
                }).toList();
        return toStop;
    }
}
