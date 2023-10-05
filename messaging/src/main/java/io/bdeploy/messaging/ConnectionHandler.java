package io.bdeploy.messaging;

import java.io.Closeable;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.URLName;

/**
 * Basic connection handler that contains methods for connecting to a server.
 *
 * @apiNote Ensure that the open connection is terminated in {@link #close()}.
 */
public interface ConnectionHandler extends Closeable {

    /**
     * Establishes a connection to the server with the given parameters.
     * <p>
     * Calling this method will terminate the existing connection by calling {@link #close()} before opening the new one.
     *
     * @param url The {@link URLName} which contains the connection parameters
     * @throws NoSuchProviderException If no provider could be found for the given protocol
     * @throws AuthenticationFailedException For authentication failures
     * @throws MessagingException For other failures
     * @see #close()
     */
    void connect(URLName url) throws NoSuchProviderException, AuthenticationFailedException, MessagingException;

    /**
     * Terminates the open connection.
     *
     * @see Closeable
     */
    @Override
    void close();
}
