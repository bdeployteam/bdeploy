package io.bdeploy.messaging;

import jakarta.mail.event.ConnectionEvent;
import jakarta.mail.event.ConnectionListener;

/**
 * A {@link ConnectionListener} which can be used to log each {@link ConnectionEvent}.
 *
 * @param <S> The type of the source which triggered the {@link ConnectionEvent}
 */
@FunctionalInterface
public interface LoggingConnectionListener<S> extends ConnectionListener {

    @Override
    default void opened(ConnectionEvent e) {
        logHelper(e, "Opened connection to ");
    }

    @Override
    default void disconnected(ConnectionEvent e) {
        logHelper(e, "Disconnected from ");
    }

    @Override
    default void closed(ConnectionEvent e) {
        logHelper(e, "Closed connection to ");
    }

    /**
     * Gets called for each {@link ConnectionEvent}.
     *
     * @param source The source of the {@link ConnectionEvent}
     * @param info An informative {@link String}, e.g. "Opened connection to "
     */
    abstract void doTheLogging(S source, String info);

    private void logHelper(ConnectionEvent e, String info) {
        @SuppressWarnings("unchecked")
        S s = (S) e.getSource();
        doTheLogging(s, info + s.getClass().getSimpleName() + ' ');
    }
}
