package io.bdeploy.minion.security;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/locator-static")
public interface PermSvcStaticPermLocator {

    // no permission but with scope is not possible.

    @Path("/noperm/noscope")
    public PermSvc getServiceNoScopeNoPerm();

    @Path("/write/{param}")
    @RequiredPermission(permission = Permission.WRITE, scope = "param")
    public PermSvc getServiceWritePerm(@PathParam("param") String param);

    @Path("/write/noscope")
    @RequiredPermission(permission = Permission.WRITE)
    public PermSvc getServiceNoScopeWritePerm();

}
