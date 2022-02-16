package io.bdeploy.pcu;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Listener that can be added to a process controller to wait for status changes.
 * <p>
 * A distinct {@link Runnable} can be registered per target {@link ProcessState}.
 */
public class MultiStateListener implements Consumer<ProcessStateChangeDto>, AutoCloseable {

    private final ProcessController pc;
    private final Map<ProcessState, Runnable> actions = new EnumMap<>(ProcessState.class);

    /**
     * Creates a new listener that gets notified whenever the state of the process is changed.
     */
    public static MultiStateListener createFor(ProcessController pc) {
        var listener = new MultiStateListener(pc);
        pc.addStatusListener(listener);
        return listener;
    }

    private MultiStateListener(ProcessController pc) {
        this.pc = pc;
    }

    @Override
    public void close() {
        pc.removeStatusListener(this);
    }

    public MultiStateListener on(ProcessState state, Runnable consumer) {
        actions.put(state, consumer);
        return this;
    }

    @Override
    public synchronized void accept(ProcessStateChangeDto event) {
        var consumer = actions.get(event.newState);

        if (consumer != null) {
            consumer.run();
        }
    }

}
