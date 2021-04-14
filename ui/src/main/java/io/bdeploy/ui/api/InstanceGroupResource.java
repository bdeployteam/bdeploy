package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.InstanceClientAppsDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/group")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceGroupResource {

    @GET
    public List<InstanceGroupConfiguration> list();

    @PUT
    @RequiredPermission(permission = Permission.ADMIN)
    public void create(InstanceGroupConfiguration config);

    @GET
    @Path("/{group}")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public InstanceGroupConfiguration read(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void update(@ActivityScope @PathParam("group") String group, InstanceGroupConfiguration config);

    @POST
    @Path("/{group}/permissions")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public void updatePermissions(@ActivityScope @PathParam("group") String group, UserPermissionUpdateDto[] permissions);

    @DELETE
    @Path("/{group}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@ActivityScope @PathParam("group") String group);

    @GET
    @Path("/{group}/users")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public SortedSet<UserInfo> getAllUser(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void updateImage(@ActivityScope @PathParam("group") String group, @FormDataParam("image") InputStream imageData);

    @GET
    @Path("/{group}/image")
    @Produces("image/png")
    @Unsecured // required to allow requests from browser directly (e.g. CSS).
    public InputStream readImage(@ActivityScope @PathParam("group") String group);

    /**
     * Create a new unique (in the given group) UUID for an instance.
     */
    @GET
    @Path("/{group}/new-uuid")
    @RequiredPermission(permission = Permission.READ, scope = "group")
    public String createUuid(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/instance")
    @RequiredPermission(permission = Permission.READ, scope = "group")
    public InstanceResource getInstanceResource(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/product")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public ProductResource getProductResource(@ActivityScope @PathParam("group") String group);

    @GET
    @Path("/{group}/client-apps")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public Collection<InstanceClientAppsDto> listClientApps(@ActivityScope @PathParam("group") String group,
            @QueryParam("os") OperatingSystem os);

    @GET
    @Path("/list-attributes")
    public Map<String, CustomAttributesRecord> listAttributes();

    @GET
    @Path("/{group}/attributes")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public CustomAttributesRecord getAttributes(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}/attributes")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void updateAttributes(@ActivityScope @PathParam("group") String group, CustomAttributesRecord attributes);

}
