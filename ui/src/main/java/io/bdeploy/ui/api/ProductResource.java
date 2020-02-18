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
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.ProductDto;

@Path("/product")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProductResource {

    @GET
    @Path("/list")
    public List<ProductDto> list();

    @DELETE
    @Path("/{name : .+}/{tag}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@PathParam("name") String name, @PathParam("tag") String tag);

    @Path("/{name : .+}/{tag}/application")
    public ApplicationResource getApplicationResource(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/diskUsage")
    public String getProductDiskUsage(@PathParam("name") String name);

    @GET
    @Path("/{name : .+}/{tag}/useCount")
    public Long getProductUseCount(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/zip")
    public String createProductZipFile(@PathParam("name") String name, @PathParam("tag") String tag);

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Manifest.Key> upload(@FormDataParam("file") InputStream inputStream);

}
