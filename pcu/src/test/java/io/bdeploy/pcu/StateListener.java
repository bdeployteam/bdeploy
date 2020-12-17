package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Listener that can be added to a process controller to wait for status changes.
 * Enables event-driven testing without hard-coded delays.
 */
public class StateListener implements Consumer<ProcessStateChangeDto> {

    private static final Logger log = LoggerFactory.getLogger(StateListener.class);

    private final ProcessController pc;

    private CountDownLatch latch;
    private List<ProcessState> expected;
    private List<ProcessState> events;
    private LinkedList<ProcessState> remaining;

    /**
     * Creates a new listener that gets notified whenever the state of the process is changed.
     */
    public static StateListener createFor(ProcessController pc) {
        StateListener listener = new StateListener(pc);
        pc.addStatusListener(listener);
        return listener;
    }

    private StateListener(ProcessController pc) {
        this.pc = pc;
    }

    /**
     * Initializes a new synchronization object that can be used to wait until the expected states are reached.
     *
     * @param expected
     *            the expected target states.
     */
    public void expect(ProcessState... expected) {
        this.expected = Arrays.asList(expected);
        this.remaining = new LinkedList<>(Arrays.asList(expected));
        this.events = new ArrayList<>();
        this.latch = new CountDownLatch(1);
    }

    @Override
    public synchronized void accept(ProcessStateChangeDto event) {
        log.info("Process state changed to {}", event.newState);
        events.add(event.newState);
        if (remaining.isEmpty()) {
            throw new RuntimeException(
                    "No more state changes expected but got <[" + event.newState + "]>. All events <[" + events + "]>  ");
        }
        // Remove first element when matching
        ProcessState first = remaining.getFirst();
        if (first == event.newState) {
            remaining.removeFirst();
        }

        // Notify if there are no more expected states
        if (!remaining.isEmpty()) {
            return;
        }
        latch.countDown();
    }

    /**
     * Causes the current thread to wait until all desired target states have been reached or the specified waiting time
     * elapses. An exception will be thrown if the waiting time exceeds.
     */
    public void await(Duration duration) throws InterruptedException {
        boolean done = latch.await(duration.getSeconds(), TimeUnit.SECONDS);
        if (done) {
            return;
        }
        String message = "Expected states not achived within waiting time. Expected <%1$s> but got <%2$s>.\n%3$s";
        fail(String.format(message, expected, events, pc.toString()));
    }

}
