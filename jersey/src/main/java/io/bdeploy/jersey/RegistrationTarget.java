package io.bdeploy.jersey;

import java.security.KeyStore;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.websockets.WebSocketApplication;

/**
 * Describes receiver for JAX-RS registrations (resources, providers, ...).
 */
public interface RegistrationTarget {

    /**
     * @return the {@link KeyStore} which can be used to perform token validation.
     */
    public KeyStore getKeyStore();

    /**
     * @param o a {@link Class} or {@link Object} instance to register.
     */
    public void register(Object o);

    /**
     * @param o register a handler for the root of the server ("/")
     */
    public void registerRoot(HttpHandler o);

    /**
     * Registers a resource that will be closed when the server is stopped.
     *
     * @param resource
     *            resource to close
     */
    public void registerResource(AutoCloseable resource);

    /**
     * Registers a WebSocket application.
     * <p>
     * Note: All {@link WebSocketApplication}s are registered in the '/ws' context path.
     *
     * @param urlMapping the path where to host the application. May contain wildcards.
     * @param wsa the WebSocketApplication
     */
    public void registerWebsocketApplication(String urlMapping, WebSocketApplication wsa);

}
