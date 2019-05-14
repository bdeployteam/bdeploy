package io.bdeploy.jersey;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

/**
 * Bridges a request's context into an {@link InheritableThreadLocal} which makes it accessible to child threads.
 * <p>
 * This is required for instance by {@link JerseySseActivityReporter} which needs context information while starting activities on
 * {@link Thread}s forked by the request {@link Thread}.
 */
@Provider
public class JerseyMultiThreadingContextBridge implements ContainerRequestFilter {

    private static InheritableThreadLocal<ContainerRequestContext> currentContext = new InheritableThreadLocal<>();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        currentContext.set(requestContext);
    }

    public static ContainerRequestContext current() {
        return currentContext.get();
    }

}
