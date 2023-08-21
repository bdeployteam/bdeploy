package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.ui.dto.HiveEntryDto;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/hive")
@RequiredPermission(permission = Permission.WRITE, scope = "hive", scopeOptional = true)
public interface HiveResource {

    @GET
    @Path("/listHives")
    public List<String> listHives();

    @GET
    @Path("/listManifests")
    public List<HiveEntryDto> listManifests(@ActivityScope @QueryParam("hive") String hiveParam);

    @GET
    @Path("/listManifest")
    public List<HiveEntryDto> listManifest(@ActivityScope @QueryParam("hive") String hiveParam, @QueryParam("name") String name,
            @QueryParam("tag") String tag);

    @GET
    @Path("/list")
    public List<HiveEntryDto> list(@ActivityScope @QueryParam("hive") String hiveParam, @QueryParam("id") String id);

    @GET
    @Path("/download")
    public Response download(@ActivityScope @QueryParam("hive") String hiveParam, @QueryParam("id") String id);

    @GET
    @Path("/repair-and-prune")
    @RequiredPermission(permission = Permission.ADMIN, scope = "hive")
    public RepairAndPruneResultDto repairAndPrune(@ActivityScope @QueryParam("hive") String hive, @QueryParam("fix") boolean fix);

    @DELETE
    @Path("/delete")
    @RequiredPermission(permission = Permission.ADMIN, scope = "hive")
    public void delete(@ActivityScope @QueryParam("hive") String hive, @QueryParam("name") String manifestName,
            @QueryParam("tag") String manifestTag);

    @Path("/{hive}/logging")
    public HiveLoggingResource getLoggingResource(@ActivityScope @PathParam("hive") String hive);
}
