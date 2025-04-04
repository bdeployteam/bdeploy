package io.bdeploy.ui.api;

import java.util.List;
import java.util.SortedSet;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.RequiredPermissionScope;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.jersey.Scope;
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
import jakarta.ws.rs.core.MediaType;

@Path("/softwarerepository")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareRepositoryResource {

    @GET
    public List<SoftwareRepositoryConfiguration> list();

    @PUT
    @RequiredPermission(permission = Permission.ADMIN)
    public void create(SoftwareRepositoryConfiguration config);

    @GET
    @Path("/{repo}")
    public SoftwareRepositoryConfiguration read(@Scope @PathParam("repo") String repo);

    @POST
    @Path("/{repo}")
    @RequiredPermission(permission = Permission.ADMIN, scope = "repo")
    public void update(@Scope @PathParam("repo") String repo, SoftwareRepositoryConfiguration config);

    @DELETE
    @Path("/{repo}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@Scope @PathParam("repo") String repo);

    @Path("/{softwareRepository}/content")
    public SoftwareResource getSoftwareResource(@Scope @PathParam("softwareRepository") String softwareRepository);

    @Path("/{softwareRepository}/product")
    @RequiredPermissionScope(scope = "softwareRepository")
    public ProductResource getProductResource(@Scope @PathParam("softwareRepository") String softwareRepository);

    @GET
    @Path("/{repo}/users")
    @RequiredPermission(permission = Permission.ADMIN, scope = "repo")
    public SortedSet<UserInfo> getAllUser(@Scope @PathParam("repo") String repo);

    @GET
    @Path("/{repo}/user-groups")
    @RequiredPermission(permission = Permission.ADMIN, scope = "repo")
    public SortedSet<UserGroupInfo> getUserGroups(@Scope @PathParam("repo") String repo);

    @POST
    @Path("/{repo}/user-permissions")
    @RequiredPermission(permission = Permission.ADMIN, scope = "repo")
    public void updateUserPermissions(@Scope @PathParam("repo") String repo, UserPermissionUpdateDto[] permissions);

    @POST
    @Path("/{repo}/user-group-permissions")
    @RequiredPermission(permission = Permission.ADMIN, scope = "repo")
    public void updateUserGroupPermissions(@Scope @PathParam("repo") String repo, UserGroupPermissionUpdateDto[] permissions);

    @POST
    @Path("/product-version/latest")
    public ProductKeyWithSourceDto getLatestProductVersion(LatestProductVersionRequestDto req);

}
