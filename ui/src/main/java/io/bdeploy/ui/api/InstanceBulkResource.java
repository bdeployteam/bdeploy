package io.bdeploy.ui.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Bulk operations for instances.
 */
@Path("/bulk")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceBulkResource {

    /**
     * Updates multiple instances to a single target version. All instances must be based on the same product.
     *
     * @param instances the instances to update.
     * @param productTag the target product version
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkUpdate/{tag}")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto updateBulk(List<Manifest.Key> instances, @PathParam("tag") String productTag);

    /**
     * @param instances instances to start. All `INSTANCE` start-type processes will be started.
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkStart")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto startBulk(List<String> instances);

    /**
     * @param instances instances to restart.
     *            All processes of those instances will be stopped.
     *            All `INSTANCE` start-type processes will be started.
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkRestart")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto restartBulk(List<String> instances);

    /**
     * @param instances instances to stop. All processes of those instances will be stopped.
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkStop")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto stopBulk(List<String> instances);

    /**
     * @param instances instances to delete
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkDelete")
    @RequiredPermission(permission = Permission.ADMIN)
    public BulkOperationResultDto deleteBulk(Set<String> instances);

    /**
     * @param instances instances where the current latest version is to be installed.
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkInstall")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto installLatestBulk(List<String> instances);

    /**
     * @param instances instances where the current latest version is to be activated.
     * @return a result for each processed instance
     */
    @POST
    @Path("/bulkActivate")
    @RequiredPermission(permission = Permission.WRITE)
    public BulkOperationResultDto activateLatestBulk(List<String> instances);

    /**
     * @param instances a {@link Set} of locally existing instance root manifest keys which are used to identify a set of servers
     *            to synchronize and update state. If <code>null</code> or {@link Collection#isEmpty() empty}, all instances of
     *            the group will be synchronized.
     * @return a {@link List} of overall instance states for all synchronized instances
     */
    @POST
    @Path("/bulkSync")
    public List<InstanceOverallStatusDto> syncBulk(Set<Manifest.Key> instances);

}
