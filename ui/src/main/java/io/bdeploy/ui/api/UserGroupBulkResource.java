package io.bdeploy.ui.api;

import java.util.Set;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.UserGroupBulkAssignPermissionDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredPermission(permission = Permission.ADMIN)
public interface UserGroupBulkResource {

    @POST
    @Path("/delete")
    public BulkOperationResultDto deleteUserGroups(Set<String> groupIds);

    @POST
    @Path("/inactive/{inactive}")
    public BulkOperationResultDto setInactiveUserGroups(@PathParam("inactive") boolean inactive, Set<String> groupIds);

    @POST
    @Path("/permission")
    public BulkOperationResultDto assignPermission(UserGroupBulkAssignPermissionDto dto);

}
