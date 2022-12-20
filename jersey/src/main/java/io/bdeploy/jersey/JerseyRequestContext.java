package io.bdeploy.jersey;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.container.ContainerRequestContext;

@Service
public class JerseyRequestContext {

    private static final Logger log = LoggerFactory.getLogger(JerseyRequestContext.class);

    @Inject
    private Provider<ContainerRequestContext> reqCtx;

    public Object getProperty(String name) {
        try {
            return this.reqCtx.get().getProperty(name);
        } catch (Exception e) {
            log.warn("Failed to get property {}", name);
            if (log.isDebugEnabled()) {
                log.debug("Error:", e);
            }
            return null;
        }
    }

    public void setProperty(String name, Object object) {
        try {
            this.reqCtx.get().setProperty(name, object);
        } catch (Exception e) {
            log.warn("Failed to set property {}", name);
            if (log.isDebugEnabled()) {
                log.debug("Error:", e);
            }
        }
    }

    public void removeProperty(String name) {
        try {
            reqCtx.get().removeProperty(name);
        } catch (Exception e) {
            log.warn("Failed to remove property {}", name);
            if (log.isDebugEnabled()) {
                log.debug("Error:", e);
            }
        }
    }
}
