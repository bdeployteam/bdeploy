package io.bdeploy.ui.api;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserChangePasswordDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuthResource {

    /**
     * @param credentials the credentials to check
     * @return a signed token if authentication succeeded
     */
    @POST
    @Unsecured
    public Response authenticate(CredentialsApi credentials);

    /**
     * Same as {@link #authenticate(CredentialsApi)} but returns a authentication pack suitable for CLI and other tools.
     */
    @POST
    @Path("/packed")
    @Unsecured
    public Response authenticatePacked(CredentialsApi credentials);

    /**
     * Return a list of recently used instance groups.
     * <p>
     * NOTE: since the list is maintained per user, there is no way to remove recently used entries efficiently when deleting an
     * instance group, thus the list may contain instance groups which are no longer available.
     *
     * @return the recently used instance groups. An instance group is recently used once the current user requested the list of
     *         instances in an instance group. The list is ordered to have the most recently used item first.
     */
    @GET
    @Path("/recent-groups")
    public List<String> getRecentlyUsedInstanceGroups();

    /**
     * Retrieve the current user.
     * <p>
     * The password field is cleared out.
     *
     * @return the currently logged in user's information.
     */
    @GET
    @Path("/user")
    public UserInfo getCurrentUser();

    /**
     * Updates the current user with the given information.
     *
     * @param info the info for the current user.
     */
    @POST
    @Path("/user")
    public void updateCurrentUser(UserInfo info);

    /**
     * Updates the current user's password.
     *
     * @param dto password data
     */
    @POST
    @Path("/change-password")
    public Response changePassword(UserChangePasswordDto dto);

    /**
     * @return an authentication pack which can be used for build integrations and command line token authentication.
     */
    @GET
    @Path("/auth-pack")
    @Produces(MediaType.TEXT_PLAIN)
    public String getAuthPack(@QueryParam("user") String user, @QueryParam("full") Boolean full);

    /**
     * @return the administrative interface for user managements.
     */
    @Path("/admin")
    @RequiredPermission(permission = Permission.ADMIN)
    public AuthAdminResource getAdmin();
}
