package io.bdeploy.messaging;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

import jakarta.mail.URLName;

/**
 * Basic connection handler that contains methods for connecting to a server.
 *
 * @apiNote Ensure that the open connection is terminated in {@link #disconnect()}.
 */
public interface ConnectionHandler extends Closeable {

    /**
     * Establishes a connection to the server with the given parameters.
     * <br>
     * Internally just calls {@link #connect(URLName, boolean)} with testMode set to <code>false</code>.
     *
     * @param url The {@link URLName} which contains the connection parameters
     * @return A {@link CompletableFuture} of the connection process, or <code>null</code> if the given url was <code>null</code>
     * @throws IllegalArgumentException If the given url is <code>null</code>
     * @see #connect(URLName, boolean)
     * @see #disconnect()
     */
    default CompletableFuture<Void> connect(URLName url) {
        return connect(url, false);
    }

    /**
     * Establishes a connection to the server with the given parameters.
     *
     * @param url The {@link URLName} which contains the connection parameters
     * @param testMode If <code>true</code>, instructs the {@link ConnectionHandler} to start in test mode. This may have no
     *            effect at all, depending on the implementation.
     * @return A {@link CompletableFuture} of the connection process, or <code>null</code> if the given url was <code>null</code>
     * @throws IllegalArgumentException If the given url is <code>null</code>
     * @see #disconnect()
     */
    CompletableFuture<Void> connect(URLName url, boolean testMode);

    /**
     * Terminates the open connection. Does nothing if there is no open connection.
     *
     * @see #connect(URLName)
     * @see #connect(URLName, boolean)
     */
    void disconnect();

    /**
     * Closes the {@link ConnectionHandler}. By default this just calls {@link #disconnect()}.
     */
    @Override
    default void close() {
        disconnect();
    }
}
