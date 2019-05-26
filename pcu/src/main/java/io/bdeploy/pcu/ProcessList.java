package io.bdeploy.pcu;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessListDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;

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
     * Returns a collection of all applications with the given state.
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
