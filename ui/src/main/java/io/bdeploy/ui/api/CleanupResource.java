package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
