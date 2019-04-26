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
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
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

    @GET
    @Unsecured
    @Path("/download/{token}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadProduct(@PathParam("token") String token);

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public List<ProductDto> upload(@FormDataParam("file") InputStream inputStream);

}
