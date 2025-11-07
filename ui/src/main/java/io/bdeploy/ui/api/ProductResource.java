package io.bdeploy.ui.api;

import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.InstanceUsageDto;
import io.bdeploy.ui.dto.ProductDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/product")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProductResource {

    /**
     * List all products, optionally filtering for a certain name.
     *
     * @param name the name to filter for or null to list all products in the instance group.
     * @return a sorted list of products. Names are sorted lexically, versions are sorted descending (newest version first).
     */
    @GET
    @Path("/list")
    public List<ProductDto> list(@QueryParam("name") String name);

    @DELETE
    @Path("/{name : .+}/{tag}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@PathParam("name") String name, @PathParam("tag") String tag);

    @Path("/{name : .+}/{tag}/application")
    @RequiredPermission(permission = Permission.READ)
    public ApplicationResource getApplicationResource(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/usedIn")
    @RequiredPermission(permission = Permission.READ)
    public List<InstanceUsageDto> getProductUsedIn(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/zip")
    @RequiredPermission(permission = Permission.READ)
    public String createProductZipFile(@PathParam("name") String name, @PathParam("tag") String tag);

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Manifest.Key> upload(FormDataMultiPart fdmp);

    @POST
    @Path("/copy")
    @RequiredPermission(permission = Permission.WRITE)
    public void copyProduct(@QueryParam("repo") String softwareRepository, @QueryParam("name") String productName,
            @QueryParam("tags") List<String> productTag);

    @GET
    @Path("/{name : .+}/{tag}/config/{file: .+}")
    @RequiredPermission(permission = Permission.READ)
    public String loadConfigFile(@PathParam("name") String name, @PathParam("tag") String tag, @PathParam("file") String file);

    @GET
    @Path("/get-response-file")
    @RequiredPermission(permission = Permission.READ)
    public String getResponseFile(@QueryParam("productId") String productId, @QueryParam("version") String version,
            @QueryParam("instanceTemplate") String instanceTemplate, @QueryParam("includeDefaults") Boolean includeDefaults);

    @Path("/bulk")
    public ProductBulkResource getBulkResource();
}
