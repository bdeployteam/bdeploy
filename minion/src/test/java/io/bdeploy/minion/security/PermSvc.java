package io.bdeploy.minion.security;

import io.bdeploy.common.security.NoScopeInheritance;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/perm")
@Produces(MediaType.APPLICATION_JSON)
public interface PermSvc {

    @Path("/read")
    @GET
    @RequiredPermission(permission = Permission.READ)
    public ObjectScope read();

    @Path("/write")
    @GET
    @RequiredPermission(permission = Permission.WRITE)
    public ObjectScope write();

    @Path("/admin")
    @GET
    @RequiredPermission(permission = Permission.ADMIN)
    public ObjectScope admin();

    @Path("/adminNoInherit")
    @GET
    @NoScopeInheritance
    @RequiredPermission(permission = Permission.ADMIN)
    public ObjectScope adminNoInherit();

}
