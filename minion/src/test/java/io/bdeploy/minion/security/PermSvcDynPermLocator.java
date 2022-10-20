package io.bdeploy.minion.security;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/locator")
public interface PermSvcDynPermLocator {

    @Path("{param}")
    @RequiredPermission(scope = "param", permission = Permission.READ, dynamicPermission = "getRequiredPermission")
    public PermSvc getScopeService(@PathParam("param") String name);

    public Permission getRequiredPermission(String name);

}
