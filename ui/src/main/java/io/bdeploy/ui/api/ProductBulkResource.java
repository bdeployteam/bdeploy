package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Bulk operations for products.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProductBulkResource {

    @POST
    @Path("/delete")
    @RequiredPermission(permission = Permission.ADMIN)
    public BulkOperationResultDto delete(List<Manifest.Key> keys);

}
