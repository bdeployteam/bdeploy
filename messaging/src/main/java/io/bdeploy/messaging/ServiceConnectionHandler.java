package io.bdeploy.messaging;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Service;
import jakarta.mail.Session;
import jakarta.mail.URLName;

/**
 * Handles the opening and closing of a connection to a {@link Service}.
 *
 * @param <S> The type of {@link Service} to handle the connection to
 */
public abstract class ServiceConnectionHandler<S extends Service> implements ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceConnectionHandler.class);

    private String protocol;
    private Session session;
    private S service;

    @Override
    public void connect(URLName url) throws NoSuchProviderException, AuthenticationFailedException, MessagingException {
        if (service != null && service.isConnected()) {
            URLName currentConnection = service.getURLName();
            if (currentConnection.getProtocol().equalsIgnoreCase(url.getProtocol())//
                    && currentConnection.getHost().equalsIgnoreCase(url.getHost())//
                    && currentConnection.getPort() == url.getPort()//
                    && currentConnection.getUsername().equals(url.getUsername())) {
                log.trace("Skipped connection because current URL equals new URL.");
                return;
            }
        }

        close();

        boolean traceEnabled = log.isTraceEnabled();
        if (log.isTraceEnabled()) {
            log.trace("Attempting connection to " + url);
        }

        protocol = url.getProtocol();
        if (protocol == null) {
            throw new NoSuchProviderException("null");
        }
        protocol = protocol.trim().toLowerCase();

        Properties properties = new Properties();
        modifyProperties(properties);

        if (traceEnabled) {
            log.trace("Properties: " + properties);
        }

        session = createSession(properties);
        service = createService(url);
        service.addConnectionListener((LoggingConnectionListener<S>) (source, info) -> log.info(info + source.getURLName()));
        service.connect();

        afterConnect(url);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the {@link ServiceConnectionHandler} by {@link Service#close() closing} the internal {@link Service}.
     *
     * @see Service#close()
     */
    @Override
    public void close() {
        if (service != null && service.isConnected()) {
            try {
                service.close();
            } catch (MessagingException e) {
                log.error("Exception while closing service " + service.getURLName(), e);
            }
        }
    }

    protected String getProtocol() {
        return protocol;
    }

    protected Session getSession() {
        return session;
    }

    protected S getService() {
        return service;
    }

    /**
     * Called after the internal {@link Service} got connected.
     */
    protected void afterConnect(URLName url) throws MessagingException {
        // Only a hook for subclasses - does nothing by default
    }

    protected abstract void modifyProperties(Properties properties);

    protected abstract Session createSession(Properties properties) throws NoSuchProviderException;

    protected abstract S createService(URLName url) throws NoSuchProviderException;
}
