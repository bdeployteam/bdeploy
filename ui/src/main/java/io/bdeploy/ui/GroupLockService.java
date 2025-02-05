package io.bdeploy.ui;

import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jvnet.hk2.annotations.Service;

/**
 * Provides a {@link java.util.concurrent.locks.ReadWriteLock} per instance group on the server to be able to serialize operations
 * on an instance group which should happen after each other atomically
 * <p>
 * An example for this is synchronizing a managed server and reacting to spawning manifests in the according BHive - all spawns
 * should happen only after synchronization has completed in this case.
 */
@Service
public class GroupLockService {

    private final TreeMap<String, ReadWriteLock> locks = new TreeMap<>();

    public ReadWriteLock getLock(String group) {
        return locks.computeIfAbsent(group, k -> new ReentrantReadWriteLock());
    }

}
