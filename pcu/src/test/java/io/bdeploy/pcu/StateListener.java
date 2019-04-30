package io.bdeploy.pcu;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Listener that can be added to a process controller to wait for status changes.
 * Enables event-driven testing without hard-coded delays.
 */
public class StateListener implements Consumer<ProcessState> {

    private CountDownLatch latch;
    private List<ProcessState> expected;

    /**
     * Initializes a new synchronization object that can be used to wait until the expected states are reached.
     *
     * @param expected
     *            the expected target states.
     */
    public void expect(ProcessController pc, ProcessState... expected) {
        this.expected = Lists.newArrayList(expected);
        this.latch = new CountDownLatch(1);
        this.accept(pc.getState());
    }

    @Override
    public void accept(ProcessState current) {
        if (expected.isEmpty()) {
            throw new RuntimeException("Unexpected state cange: " + current);
        }
        // Remove first element when matching
        if (expected.get(0) == current) {
            expected.remove(0);
        }

        // Notify if there are no more expected states
        if (!expected.isEmpty()) {
            return;
        }
        latch.countDown();
    }

    /**
     * Causes the current thread to wait until all desired target states have been reached or the specified waiting time
     * elapses. An exception will be thrown if the waiting time exceeds.
     */
    public void await(Duration duration) {
        try {
            boolean done = latch.await(duration.getSeconds(), TimeUnit.SECONDS);
            if (!done) {
                throw new RuntimeException("Timout occured while waiting for expected states: " + expected);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for expected states: " + expected);
        }
    }

}
