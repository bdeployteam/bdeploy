package io.bdeploy.ui.api;

import java.util.List;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.jersey.ActivityScope;

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
    public SoftwareRepositoryConfiguration read(@ActivityScope @PathParam("repo") String repo);

    @POST
    @Path("/{repo}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void update(@ActivityScope @PathParam("repo") String repo, SoftwareRepositoryConfiguration config);

    @DELETE
    @Path("/{repo}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@ActivityScope @PathParam("repo") String repo);

    @Path("/{softwareRepository}/content")
    public SoftwareResource getSoftwareResource(@ActivityScope @PathParam("softwareRepository") String softwareRepository);

    @GET
    @Path("/{repo}/users")
    @RequiredPermission(permission = Permission.ADMIN)
    public SortedSet<UserInfo> getAllUser(@ActivityScope @PathParam("repo") String repo);

    @POST
    @Path("/{repo}/permissions")
    @RequiredPermission(permission = Permission.ADMIN)
    public void updatePermissions(@ActivityScope @PathParam("repo") String repo, UserPermissionUpdateDto[] permissions);

}
