package io.bdeploy.jersey;

import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
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
     * @param handler register an additional handler at the given location. pass {@link HttpHandlerRegistration#ROOT} to register
     *            at the
     *            root of the server (e.g. the root web resources).
     */
    public void addHandler(HttpHandler handler, HttpHandlerRegistration reg);

    /**
     * @param handler a previously registered handler.
     */
    public void removeHandler(HttpHandler handler);

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

    /**
     * @return a {@link CompletableFuture} which is completed after the server has completed startup.
     */
    public CompletionStage<RegistrationTarget> afterStartup();

}
