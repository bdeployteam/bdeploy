package io.bdeploy.jersey;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;

/**
 * Queries annotations on resource implementations for {@link LockingResource} and {@link WriteLock} annotations and locks
 * accordingly.
 */
@Provider
public class JerseyWriteLockFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(JerseyWriteLockFilter.class);

    private static final String LOCK_KEY = "ResourceLock";
    private static final long LOCK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    @Inject
    private JerseyWriteLockService lockService;

    @Context
    private ResourceInfo ri;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LockingResource lr = ri.getResourceClass().getAnnotation(LockingResource.class);
        if (lr == null) {
            return;
        }

        // find, lock, remember
        String path = requestContext.getUriInfo().getPath(false);
        ReadWriteLock lock = lockService.getLock(lr.value().isEmpty() ? path : lr.value());

        boolean write = ri.getResourceMethod().isAnnotationPresent(WriteLock.class);
        Lock rwLock;
        if (write) {
            log.debug("Write-locking " + path);
            rwLock = lock.writeLock();
        } else {
            log.debug("Read-locking " + path);
            rwLock = lock.readLock();
        }

        boolean locked = false;
        try {
            locked = rwLock.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!locked) {
            requestContext.abortWith(
                    Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Cannot acquire resource lock").build());
            return;
        }

        requestContext.setProperty(LOCK_KEY, rwLock);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // find remembered, unlock
        Lock rwLock = (Lock) requestContext.getProperty(LOCK_KEY);
        if (rwLock != null) {
            log.debug("Unlocking " + requestContext.getUriInfo().getPath(false));
            rwLock.unlock();
        }
    }

}
