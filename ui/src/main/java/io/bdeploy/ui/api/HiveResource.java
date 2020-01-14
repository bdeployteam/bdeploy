package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import io.bdeploy.common.security.RequiredCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.ui.dto.HiveEntryDto;

@Path("/hive")
@RequiredCapability(capability = Capability.ADMIN)
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
    @Path("/downloadManifest")
    public Response downloadManifest(@ActivityScope @QueryParam("hive") String hiveParam, @QueryParam("name") String name,
            @QueryParam("tag") String tag);

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
