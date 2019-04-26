package io.bdeploy.pcu;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Listener that can be added to a process controller to wait for status changes.
 * Enables event-driven testing without hard-coded delays.
 */
public class CountDownListener implements Consumer<ProcessState> {

    private CountDownLatch latch;
    private List<ProcessState> expected;

    /**
     * Creates and returns a new synchronization that is notified whenever status changed.
     *
     * @param expected
     *            expected state
     * @return latch the synchronization that is notified
     */
    public CountDownLatch expect(ProcessController pc, ProcessState... expected) {
        this.expected = Lists.newArrayList(expected);
        this.latch = new CountDownLatch(1);
        this.accept(pc.getState());
        return latch;
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

}
