package io.bdeploy.pcu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

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
    public void startAll(String user, Map<String, ProcessController> running) {
        Instant start = Instant.now();

        for (var cfg : processes.controllers.entrySet()) {
            if (cfg.getValue().getDescriptor().processControl.startType == ApplicationStartType.INSTANCE
                    && cfg.getValue().getState().isStopped()) {
                // process will be started in the group at some point. mark it as such.
                cfg.getValue().prepareStart(user);
            }
        }

        boolean failing = false;
        for (ProcessControlGroupConfiguration controlGroup : processes.getControlGroups()) {
            if (!failing) {
                try (BulkControlStrategy bulk = BulkControlStrategy.create(user, instanceUuid, activeTag, controlGroup, processes,
                        controlGroup.startType)) {
                    List<String> failed = bulk.startGroup(running);

                    if (!failed.isEmpty()) {
                        logger.log(l -> l.warn("Not all applications could be started, skipping other Control Groups. Failed {}",
                                failed));
                        failing = true;
                    }
                }
            } else {
                // a PREVIOUS group failed. Instead of starting we revert to STOPPED state in case we prepared starting.
                for (var entry : processes.controllers.entrySet()) {
                    if (controlGroup.processOrder.contains(entry.getKey())
                            && entry.getValue().getState() == ProcessState.STOPPED_START_PLANNED) {
                        entry.getValue().abortStart(user);
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
    public void stopAll(String user, Map<String, ProcessController> running) {
        // use the CURRENT group configuration where possible.
        List<ProcessControlGroupConfiguration> currentGroups = processes.getControlGroups();

        // Set intent that all should be stopped - the order does not matter as all of them are set to scheduled stop IMMEDIATELY.
        for (ProcessController process : running.values()) {
            try {
                process.prepareStop(user);
            } catch (Exception ex) {
                String appId = process.getDescriptor().uid;
                String tag = process.getStatus().instanceTag;
                logger.log(l -> l.error("Failed to prepare stopping of application.", ex), tag, appId);
            }
        }

        Set<String> handled = new TreeSet<>(); // ID's which we *tried* to stop
        Set<String> stopped = new TreeSet<>(); // ID's which actually stopped.

        // reverse the groups.
        List<ProcessControlGroupConfiguration> reverseGroups = new ArrayList<>(currentGroups);
        Collections.reverse(reverseGroups);

        for (ProcessControlGroupConfiguration controlGroup : reverseGroups) {
            // reverse ordered by the current control groups order.
            List<ProcessController> toStop = running.entrySet().stream()
                    .filter(e -> controlGroup.processOrder.contains(e.getKey()))
                    .sorted((a, b) -> Integer.compare(controlGroup.processOrder.indexOf(b.getKey()),
                            controlGroup.processOrder.indexOf(a.getKey())))
                    .peek(e -> handled.add(e.getKey())).map(e -> e.getValue()).toList();

            try (BulkControlStrategy bulk = BulkControlStrategy.create(user, instanceUuid, activeTag, controlGroup, processes,
                    controlGroup.stopType)) {
                stopped.addAll(bulk.stopGroup(toStop));
            }
        }

        handled.forEach(running::remove); // everything we were able to assign to a process control group.

        // processes which are not assigned to any group in the CURRENT instance version - stopped last (different to previous BDeploy versions).
        if (!running.isEmpty()) {
            ProcessControlGroupConfiguration anonGroup = new ProcessControlGroupConfiguration();
            anonGroup.name = "Unassigned Processes";
            anonGroup.processOrder.addAll(running.keySet()); // no order.

            try (BulkControlStrategy bulk = BulkControlStrategy.create(user, instanceUuid, activeTag, anonGroup, processes,
                    anonGroup.stopType)) {
                stopped.addAll(bulk.stopGroup(running.values()));
            }
        }
    }
}
