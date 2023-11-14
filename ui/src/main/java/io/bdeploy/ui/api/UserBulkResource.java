package io.bdeploy.ui.api;

import java.util.Set;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.UserBulkAssignPermissionDto;
import io.bdeploy.ui.dto.UserBulkRemovePermissionDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredPermission(permission = Permission.ADMIN)
public interface UserBulkResource {

    @POST
    @Path("/delete")
    public BulkOperationResultDto delete(Set<String> userNames);

    @POST
    @Path("/inactive/{inactive}")
    public BulkOperationResultDto setInactive(@PathParam("inactive") boolean inactive, Set<String> userNames);

    @POST
    @Path("/assign-permission")
    public BulkOperationResultDto assignPermission(UserBulkAssignPermissionDto dto);

    @POST
    @Path("/remove-permission")
    public BulkOperationResultDto removePermission(UserBulkRemovePermissionDto dto);

    @POST
    @Path("/add-to-group/{groupId}")
    public BulkOperationResultDto addToGroup(@PathParam("groupId") String groupId, Set<String> userNames);

}
