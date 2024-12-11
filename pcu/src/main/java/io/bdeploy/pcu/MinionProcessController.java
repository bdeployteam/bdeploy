package io.bdeploy.pcu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

/**
 * Represents the top-level process controller of a minion. The controller will instantiate a new
 * {@linkplain InstanceProcessController instance controller} for each instance.
 */
public class MinionProcessController {

    private final MdcLogger logger = new MdcLogger(MinionProcessController.class);

    /** Maps the UID of an instance to its controller */
    private final Map<String, InstanceProcessController> instance2Controller = new ConcurrentHashMap<>();

    /**
     * Sets the active manifest versions for each deployed instance.
     *
     * @param versions the active versions. Key = InstanceId, Value = Version.
     */
    public void setActiveVersions(SortedMap<String, Key> versions) {
        for (Map.Entry<String, Key> entry : versions.entrySet()) {
            String instanceId = entry.getKey();
            String activeTag = entry.getValue().getTag();

            // Deleting an instance could result in an active version still being recorded
            InstanceProcessController controller = instance2Controller.get(instanceId);
            if (controller == null) {
                continue;
            }
            controller.setActiveTag(activeTag);
        }
    }

    /**
     * Recovers any processes that are still running.
     */
    public void recover() {
        doParallelAndWait("Recover", instance2Controller.values().stream().map(c -> (Runnable) (c::recover)).toList());
    }

    /**
     * Starts all applications of the activated tag if the corresponding auto-start flag is set in the configuration.
     */
    public void autoStart() {
        doParallelAndWait("AutoStart", instance2Controller.values().stream().map(c -> (Runnable) (c::autoStart)).toList());
    }

    private static void doParallelAndWait(String name, List<Runnable> logic) {
        try (ExecutorService es = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(name).factory())) {
            // need to use collection to satisfy the java compiler...
            FutureHelper.awaitAll(logic.stream().map(es::submit).collect(Collectors.toCollection(() -> new ArrayList<>())));
        }
    }

    /**
     * Returns an existing instance controller or null if no instance is not deployed.
     *
     * @param instanceId
     *            the unique ID of the instance to manage
     * @return the instance controller or {@code null}
     */
    public InstanceProcessController get(String instanceId) {
        return instance2Controller.get(instanceId);
    }

    /**
     * Returns an existing instance controller or creates a new one if not existing
     *
     * @param inm the current instance node manifest
     */
    public InstanceProcessController getOrCreate(InstanceNodeManifest inm) {
        return instance2Controller.computeIfAbsent(inm.getId(), instanceId -> {
            logger.log(l -> l.debug("Creating new instance controller."), instanceId);
            return new InstanceProcessController(instanceId);
        });
    }

    /**
     * Returns detailed information about the process launched for a given application.
     *
     * @param instanceId
     *            the instance ID
     * @param appId
     *            the application ID
     * @return the DTO containing the details
     */
    public ProcessDetailDto getDetails(String instanceId, String appId) {
        InstanceProcessController ipc = get(instanceId);
        return ipc.getDetails(appId);
    }

}
