package io.bdeploy.minion.security;

import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.api.impl.PermissionRequestFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

public class PermSvcImpl implements PermSvc {

    @Context
    private ContainerRequestContext ctx;

    @Override
    public ObjectScope read() {
        return (ObjectScope) ctx.getProperty(PermissionRequestFilter.PERM_SCOPE);
    }

    @Override
    public ObjectScope write() {
        return (ObjectScope) ctx.getProperty(PermissionRequestFilter.PERM_SCOPE);
    }

    @Override
    public ObjectScope admin() {
        return (ObjectScope) ctx.getProperty(PermissionRequestFilter.PERM_SCOPE);
    }

    @Override
    public ObjectScope adminNoInherit() {
        return (ObjectScope) ctx.getProperty(PermissionRequestFilter.PERM_SCOPE);
    }
}
