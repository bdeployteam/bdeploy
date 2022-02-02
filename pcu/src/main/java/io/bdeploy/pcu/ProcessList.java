package io.bdeploy.pcu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessListDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

/**
 * Contains all processes as well as their configuration that are belonging to the same tag.
 */
public class ProcessList {

    private final MdcLogger logger = new MdcLogger(ProcessController.class);

    /** The tag of this process list */
    private final String instanceTag;

    /** The configuration of the entire group */
    public final ProcessGroupConfiguration processConfig;

    /** Maps the ID of the application to its controller */
    public final Map<String, ProcessController> controllers = new HashMap<>();

    public final List<ProcessControlGroupConfiguration> controlGroups = new ArrayList<>();

    /** Creates a new process list */
    public ProcessList(String instanceTag, ProcessGroupConfiguration processConfig) {
        this.logger.setMdcValue(processConfig.uuid, instanceTag);
        this.instanceTag = instanceTag;
        this.processConfig = processConfig;
    }

    /**
     * Returns the tag to which the applications are belonging to.
     *
     * @return
     *         the tag of the instance
     */
    public String getInstanceTag() {
        return instanceTag;
    }

    /**
     * Adds a new process controller.
     *
     * @param controller
     *            the controller to add
     */
    public void add(ProcessController controller) {
        controllers.put(controller.getDescriptor().uid, controller);
    }

    /**
     * Adds a new process control group configuration.
     *
     * @param groups the group to add.
     */
    public void setControlGroups(List<ProcessControlGroupConfiguration> groups) {
        controlGroups.clear();
        controlGroups.addAll(groups);
    }

    /**
     * Grabs all process control configuration from the instance configuration. In case not configuration is present, or not all
     * processes are contained in the configuration, a default group is created and filled with those (or all) processes.
     */
    public void setControlGroupsFromConfig(InstanceNodeManifest inm) {
        List<ProcessControlGroupConfiguration> groups = inm == null ? Collections.emptyList()
                : inm.getConfiguration().controlGroups;
        ProcessControlGroupConfiguration defaultGroup = new ProcessControlGroupConfiguration();

        for (ProcessConfiguration process : processConfig.applications) {
            // check if the application is in a control group, otherwise -> default.
            if (groups.stream().filter(c -> c.processOrder.contains(process.uid)).findAny().isEmpty()) {
                defaultGroup.processOrder.add(process.uid);
            }
        }

        List<ProcessControlGroupConfiguration> controlGroups = new ArrayList<>();

        // the default group, containing all processes which are not (yet) in any group is always the first group.
        if (!defaultGroup.processOrder.isEmpty()) {
            controlGroups.add(defaultGroup);
        }

        // now the rest of the groups.
        controlGroups.addAll(groups);

        setControlGroups(controlGroups);
    }

    /**
     * Retrieves all available process control groups.
     */
    public List<ProcessControlGroupConfiguration> getControlGroups() {
        return controlGroups;
    }

    /**
     * Returns the controller for the given application
     *
     * @param applicationId
     *            the application identifier
     */
    public ProcessController get(String applicationId) {
        ProcessController controller = controllers.get(applicationId);
        if (controller == null) {
            throw new PcuRuntimeException("Unknown application: " + applicationId);
        }
        return controller;
    }

    /**
     * Returns an collection of all applications with the given state.
     *
     * @param states the states to filter
     * @return all applications with the given state
     */
    public Map<String, ProcessController> getWithState(Collection<ProcessState> states) {
        return controllers.entrySet().stream().filter(e -> states.contains(e.getValue().getState()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Recovers all running applications of this instance tag.
     *
     * @return a counter indicating how many applications are running
     */
    public int recover() {
        int running = 0;
        for (Map.Entry<String, ProcessController> entry : controllers.entrySet()) {
            String appId = entry.getKey();
            ProcessController controller = entry.getValue();

            // Recover and check success state
            controller.recover();
            boolean isRunning = controller.getState().isRunningOrScheduled();
            if (!isRunning) {
                continue;
            }
            running++;
            logger.log(l -> l.info("Application {} is running.", appId));
        }
        return running;
    }

    /**
     * Returns runtime details about this process list.
     *
     * @return information about running and deployed applications.
     */
    public ProcessListDto getStatus() {
        ProcessListDto statusDto = new ProcessListDto();
        for (ProcessController controller : controllers.values()) {
            statusDto.add(controller.getStatus());
        }
        return statusDto;
    }

}
