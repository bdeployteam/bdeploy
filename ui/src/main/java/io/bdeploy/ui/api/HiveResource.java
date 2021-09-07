package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.ui.dto.HiveEntryDto;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/hive")
@RequiredPermission(permission = Permission.ADMIN)
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
    @Path("/prune")
    public String prune(@ActivityScope @QueryParam("hive") String hive);

    @GET
    @Path("/fsck")
    public Map<String, String> fsck(@ActivityScope @QueryParam("hive") String hive, @QueryParam("fix") boolean fix);

    @DELETE
    @Path("/delete")
    public void delete(@ActivityScope @QueryParam("hive") String hive, @QueryParam("name") String manifestName,
            @QueryParam("tag") String manifestTag);
}
