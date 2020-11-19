package io.bdeploy.jersey.locking;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.Threads;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;

@LockingResource
@Singleton
public class LockedResourceImpl implements LockedResource {

    private static final Logger log = LoggerFactory.getLogger(LockedResourceImpl.class);

    private final AtomicBoolean inChange = new AtomicBoolean(false);
    private String value = "Hello";

    @Override
    public String getValue() {
        return value;
    }

    @Override
    @WriteLock
    public void setValue(String value) {
        if (!inChange.compareAndSet(false, true)) {
            throw new IllegalStateException("Only on thread allowed in update");
        }

        log.info("setting value to {}", value);
        this.value = value;
        if (!Threads.sleep(500)) {
            return;
        }

        if (!inChange.compareAndSet(true, false)) {
            throw new IllegalStateException("Unexpected state in resetting");
        }
    }

    @Override
    public void setValueUnlocked(String value) {
        setValue(value); // bypasses locking as not annotated on resource
    }

}
