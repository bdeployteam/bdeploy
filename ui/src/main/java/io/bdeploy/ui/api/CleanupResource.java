package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.cleanup.CleanupGroup;

@Path("/cleanUi")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredPermission(permission = Permission.ADMIN)
public interface CleanupResource {

    @GET
    public List<CleanupGroup> calculate();

    @POST
    public void perform(List<CleanupGroup> groups);

}
