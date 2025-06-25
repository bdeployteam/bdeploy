package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.settings.CustomDataGrouping;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.jersey.Scope;
import io.bdeploy.ui.dto.InstanceAllClientsDto;
import io.bdeploy.ui.dto.LatestProductVersionRequestDto;
import io.bdeploy.ui.dto.ProductKeyWithSourceDto;
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
import jakarta.ws.rs.core.Response;

@Path("/group")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceGroupResource {

    @GET
    public List<InstanceGroupConfigurationDto> list();

    @PUT
    @RequiredPermission(permission = Permission.ADMIN)
    public void create(InstanceGroupConfiguration config);

    @GET
    @Path("/{group}")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public InstanceGroupConfigurationDto getInstanceGroupConfigurationDto(@Scope @PathParam("group") String group);

    @POST
    @Path("/{group}")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void update(@Scope @PathParam("group") String group, InstanceGroupConfiguration config);

    @DELETE
    @Path("/{group}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@Scope @PathParam("group") String group);

    @GET
    @Path("/{group}/users")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public SortedSet<UserInfo> getAllUser(@Scope @PathParam("group") String group);

    @GET
    @Path("/{group}/user-groups")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public SortedSet<UserGroupInfo> getAllUserGroup(@Scope @PathParam("group") String group);

    @POST
    @Path("/{group}/user-permissions")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public void updateUserPermissions(@Scope @PathParam("group") String group, UserPermissionUpdateDto[] permissions);

    @POST
    @Path("/{group}/user-group-permissions")
    @RequiredPermission(permission = Permission.ADMIN, scope = "group")
    public void updateUserGroupPermissions(@Scope @PathParam("group") String group, UserGroupPermissionUpdateDto[] permissions);

    @POST
    @Path("/{group}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void updateImage(@Scope @PathParam("group") String group, FormDataMultiPart fdmp);

    @DELETE
    @Path("/{group}/image")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void deleteImage(@Scope @PathParam("group") String group);

    @GET
    @Path("/{group}/image")
    @Produces("image/png")
    @Unsecured // required to allow requests from browser directly (e.g. CSS).
    public Response readImage(@Scope @PathParam("group") String group);

    /**
     * Create a new unique (in the given group) UUID for an instance.
     */
    @GET
    @Path("/{group}/new-uuid")
    @RequiredPermission(permission = Permission.READ, scope = "group")
    public String createId(@Scope @PathParam("group") String group);

    @Path("/{group}/instance")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public InstanceResource getInstanceResource(@Scope @PathParam("group") String group);

    @Path("/{group}/system")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public SystemResource getSystemResource(@Scope @PathParam("group") String group);

    @Path("/{group}/product")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public ProductResource getProductResource(@Scope @PathParam("group") String group);

    @GET
    @Path("/{group}/all-clients")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public InstanceAllClientsDto listAllClients(@Scope @PathParam("group") String group, @QueryParam("os") OperatingSystem os);

    @GET
    @Path("/list-attributes")
    public Map<String, CustomAttributesRecord> listAttributes();

    @GET
    @Path("/{group}/attributes")
    @RequiredPermission(permission = Permission.CLIENT, scope = "group")
    public CustomAttributesRecord getAttributes(@Scope @PathParam("group") String group);

    @POST
    @Path("/{group}/attributes")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void updateAttributes(@Scope @PathParam("group") String group, CustomAttributesRecord attributes);

    @PUT
    @Path("/{group}/presets")
    @RequiredPermission(permission = Permission.WRITE, scope = "group")
    public void updatePreset(@Scope @PathParam("group") String group, @QueryParam("multiple") boolean multiple,
            List<CustomDataGrouping> preset);

    @POST
    @Path("/product-version/latest")
    public ProductKeyWithSourceDto getLatestProductVersion(LatestProductVersionRequestDto req);

    @POST
    @Path("/{group}/invalidate-caches")
    public void invalidateCaches(@PathParam("group") String group);
}
