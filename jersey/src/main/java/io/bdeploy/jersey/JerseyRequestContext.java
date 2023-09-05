package io.bdeploy.jersey;

import java.util.Map;
import java.util.TreeMap;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.NoThrowAutoCloseable;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Provides a wrapper around the {@link ContainerRequestContext} where per-request data is stored.
 * <p>
 * In case a new Thread is started *within* a request, the request association is lost if not handled explicitly, thus we
 * need to fall back to a simple thread local solution.
 */
@Service
public class JerseyRequestContext {

    private static final Logger log = LoggerFactory.getLogger(JerseyRequestContext.class);

    private final ThreadLocal<ContainerRequestContext> reqCtx = new ThreadLocal<>();

    private final ThreadLocal<Map<String, Object>> override = new ThreadLocal<>();
    private final ThreadLocal<Map<String, Object>> fallback = ThreadLocal.withInitial(() -> new TreeMap<>());

    public void setRequest(ContainerRequestContext requestContext) {
        reqCtx.set(requestContext);
    }

    public Object getProperty(String name) {
        if (override.get() != null) {
            return override.get().get(name);
        }

        var ctx = reqCtx.get();
        if (ctx != null) {
            try {
                return ctx.getProperty(name);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to get property {}", name, e);
                }
            }
        }

        return fallback.get().get(name);
    }

    public void setProperty(String name, Object object) {
        if (override.get() != null) {
            override.get().put(name, object);
        }

        var ctx = reqCtx.get();
        if (ctx != null) {
            try {
                ctx.setProperty(name, object);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to set property {}", name, e);
                }
            }
        }
        fallback.get().put(name, object);
    }

    public void removeProperty(String name) {
        if (override.get() != null) {
            override.get().remove(name);
        }

        var ctx = reqCtx.get();
        if (ctx != null) {
            try {
                ctx.removeProperty(name);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to remove property {}", name, e);
                }
            }
        }
        fallback.get().remove(name);
    }

    public void clear() {
        // cleanup potential thread local state.
        override.remove();
        fallback.remove();
    }

    /**
     * Branches the context for a new thread. This is used to avoid parallel manipulation of the same context with different
     * information from multiple threads.
     */
    public NoThrowAutoCloseable branchThread() {
        override.set(new TreeMap<>());
        return override::remove;
    }

}
