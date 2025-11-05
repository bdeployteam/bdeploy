package io.bdeploy.ui.api;

import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.UploadInfoDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareResource {

    @GET
    public List<Manifest.Key> list(@QueryParam("products") boolean products, @QueryParam("generic") boolean generic);

    @DELETE
    @Path("/{name : .+}/{tag}")
    @RequiredPermission(permission = Permission.WRITE, scope = "softwareRepository")
    public void delete(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/zip")
    public String createSoftwareZipFile(@PathParam("name") String name, @PathParam("tag") String tag);

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE, scope = "softwareRepository")
    public List<Manifest.Key> upload(FormDataMultiPart fdmp);

    @POST
    @Path("/upload-raw-content")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE, scope = "softwareRepository")
    public UploadInfoDto uploadRawContent(FormDataMultiPart fdmp, @QueryParam("name") String manifestName,
            @QueryParam("tag") String manifestTag, @QueryParam("os") String supportedOS);

    @POST
    @Path("/import-raw-content")
    @RequiredPermission(permission = Permission.WRITE, scope = "softwareRepository")
    public UploadInfoDto importRawContent(UploadInfoDto dto);

    @Path("/bulk")
    @RequiredPermission(permission = Permission.ADMIN, scope = "softwareRepository")
    public SoftwareBulkResource getBulkResource();
}
