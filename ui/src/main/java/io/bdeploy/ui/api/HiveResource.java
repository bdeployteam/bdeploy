package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.jersey.Scope;
import io.bdeploy.ui.dto.HiveEntryDto;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/hive")
@RequiredPermission(permission = Permission.READ, scope = "hive", scopeOptional = true)
public interface HiveResource {

    @GET
    @Path("/listHives")
    public List<String> listHives();

    @GET
    @Path("/listManifests")
    public List<HiveEntryDto> listManifests(@Scope @QueryParam("hive") String hiveParam);

    @GET
    @Path("/listManifest")
    public List<HiveEntryDto> listManifest(@Scope @QueryParam("hive") String hiveParam, @QueryParam("name") String name,
            @QueryParam("tag") String tag);

    @GET
    @Path("/list")
    public List<HiveEntryDto> list(@Scope @QueryParam("hive") String hiveParam, @QueryParam("id") String id);

    @GET
    @Path("/download")
    public Response download(@Scope @QueryParam("hive") String hiveParam, @QueryParam("id") String id);

    @POST
    @Path("/download")
    public Response download(@Scope @QueryParam("hive") String hiveParam, HiveEntryDto dto);

    @GET
    @Path("/repair-and-prune")
    @RequiredPermission(permission = Permission.ADMIN, scope = "hive")
    public RepairAndPruneResultDto repairAndPrune(@Scope @QueryParam("hive") String hive, @QueryParam("fix") boolean fix);

    @DELETE
    @Path("/delete")
    @RequiredPermission(permission = Permission.ADMIN, scope = "hive")
    public void delete(@Scope @QueryParam("hive") String hive, @QueryParam("name") String manifestName,
            @QueryParam("tag") String manifestTag);

    @Path("/{hive}/logging")
    public HiveLoggingResource getLoggingResource(@Scope @PathParam("hive") String hive);
}
