package io.bdeploy.minion.security;

import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

public class PermSvcStaticPermLocatorImpl implements PermSvcStaticPermLocator {

    @Context
    private ResourceContext rc;

    @Override
    public PermSvc getServiceNoScopeNoPerm() {
        return rc.initResource(new PermSvcImpl());
    }

    @Override
    public PermSvc getServiceWritePerm(String param) {
        return rc.initResource(new PermSvcImpl());
    }

    @Override
    public PermSvc getServiceNoScopeWritePerm() {
        return rc.initResource(new PermSvcImpl());
    }

}
