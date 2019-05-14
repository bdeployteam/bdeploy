package io.bdeploy.jersey;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jvnet.hk2.annotations.Service;

/**
 * Keeps track of all locks for locking resources
 * <p>
 * There is potentially a {@link ReentrantReadWriteLock} per registered resource as soon as every resource is used once. Locks are
 * never cleared.
 */
@Service
public class JerseyWriteLockService {

    /**
     * Marks a resource as 'locking'. This means that every method on the resource will by default read-lock a lock specific to
     * that resource.
     * <p>
     * ATTENTION: This annotation must be placed on the resource implementation!
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LockingResource {
    }

    /**
     * Specifies that the annotated method should lock the write-lock of the {@link LockingResource} instead of the read-lock.
     * <p>
     * ATTENTION: This annotation must be placed on the resource implementation!
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.METHOD)
    public @interface WriteLock {
    }

    private final Map<String, ReadWriteLock> locks = new TreeMap<>();

    public ReadWriteLock getLock(String id) {
        return locks.computeIfAbsent(id, (k) -> new ReentrantReadWriteLock());
    }

}
