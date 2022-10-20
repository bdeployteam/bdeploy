package io.bdeploy.minion.security;

import io.bdeploy.common.security.ScopedPermission.Permission;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

public class PermSvcDynPermLocatorImpl implements PermSvcDynPermLocator {

    @Inject
    private PermControlSvcImpl permControl;

    @Context
    private ResourceContext ctx;

    @Override
    public Permission getRequiredPermission(String name) {
        return permControl.getPerm();
    }

    @Override
    public PermSvc getScopeService(String name) {
        return ctx.initResource(new PermSvcImpl());
    }

}
