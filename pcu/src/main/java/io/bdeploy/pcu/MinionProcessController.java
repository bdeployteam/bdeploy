package io.bdeploy.pcu;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;

/**
 * Represents the top-level process controller of a minion. The controller will instantiate a new
 * {@linkplain InstanceProcessController instance controller} for each instance.
 */
public class MinionProcessController {

    private static final Logger log = LoggerFactory.getLogger(MinionProcessController.class);

    /** Guards access to the map */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    /** Maps the UID of an instance to its controller */
    private final Map<String, InstanceProcessController> instance2Controller = new TreeMap<>();

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
        try {
            readLock.lock();
            for (Map.Entry<String, InstanceProcessController> entry : instance2Controller.entrySet()) {
                InstanceProcessController controller = entry.getValue();
                controller.recover();
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Starts all applications of the activated tag if the corresponding auto-start flag is set in the configuration.
     */
    public void autoStart() {
        try {
            readLock.lock();
            for (Map.Entry<String, InstanceProcessController> entry : instance2Controller.entrySet()) {
                InstanceProcessController controller = entry.getValue();
                controller.autoStart();
            }
        } finally {
            readLock.unlock();
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
     * @param instanceId
     *            the unique ID of the instance to manage
     */
    public InstanceProcessController getOrCreate(String instanceId) {
        try {
            writeLock.lock();
            InstanceProcessController controller = instance2Controller.get(instanceId);
            if (controller == null) {
                controller = new InstanceProcessController(instanceId);
                instance2Controller.put(instanceId, controller);
                log.info("{} - Creating new instance controller.", instanceId);
            }
            return controller;
        } finally {
            writeLock.unlock();
        }
    }

}
