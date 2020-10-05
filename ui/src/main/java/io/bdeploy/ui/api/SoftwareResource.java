package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;

@Path("/softwarerepository")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareResource {

    @GET
    public List<Manifest.Key> list(@QueryParam("products") boolean products, @QueryParam("generic") boolean generic);

    @GET
    @Path("/{name : .+}/diskUsage")
    public String getSoftwareDiskUsage(@PathParam("name") String name);

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
    public List<Manifest.Key> upload(@FormDataParam("file") InputStream inputStream);

    @POST
    @Path("/upload-raw-content")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE, scope = "softwareRepository")
    public List<Manifest.Key> uploadRawContent(@FormDataParam("file") InputStream inputStream,
            @QueryParam("name") String manifestName, @QueryParam("tag") String manifestTag, @QueryParam("os") String supportedOS);

}
