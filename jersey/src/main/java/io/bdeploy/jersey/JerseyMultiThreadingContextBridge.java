package io.bdeploy.jersey;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * Bridges a request's context into an {@link InheritableThreadLocal} which makes it accessible to child threads.
 * <p>
 * This is required for instance by {@link JerseySseActivityReporter} which needs context information while starting activities on
 * {@link Thread}s forked by the request {@link Thread}.
 */
@Provider
public class JerseyMultiThreadingContextBridge implements ContainerRequestFilter, ContainerResponseFilter {

    private static InheritableThreadLocal<ContainerRequestContext> currentContext = new InheritableThreadLocal<>();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        currentContext.set(requestContext);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        currentContext.set(null);
    }

    public static ContainerRequestContext current() {
        return currentContext.get();
    }

}
