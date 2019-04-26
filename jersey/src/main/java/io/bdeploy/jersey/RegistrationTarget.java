package io.bdeploy.jersey;

import org.glassfish.grizzly.http.server.HttpHandler;

/**
 * Describes receiver for JAX-RS registrations (resources, providers, ...).
 */
public interface RegistrationTarget {

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

}
