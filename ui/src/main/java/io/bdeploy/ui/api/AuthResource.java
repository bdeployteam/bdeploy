package io.bdeploy.ui.api;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserChangePasswordDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserProfileInfo;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.jersey.SessionManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
     * Same as {@link #authenticate(CredentialsApi)} but returns a authentication pack suitable for CLI and
     * other tools.
     */
    @POST
    @Path("/packed")
    @Unsecured
    public Response authenticatePacked(CredentialsApi credentials);

    /**
     * Authenticates a user and begins a local web-session for that user.
     *
     * @param credentials the credentials to check
     * @param auth a special authenticator to use or <code>null</code>.
     * @return a signed token if authentication succeeded
     */
    @POST
    @Path("/session")
    @Unsecured
    public Response authenticateSession(CredentialsApi credentials, @QueryParam("auth") SpecialAuthenticators auth);

    /**
     * @return the current session full token for the issued request.
     */
    @GET
    @Path("/session")
    @Unsecured
    public Response getSessionToken(@CookieParam(SessionManager.SESSION_COOKIE) Cookie cookie, @QueryParam("otp") String otp);

    /**
     * Creates a new session with an one time password which can be used for a single login.
     *
     * @return The one time password
     */
    @POST
    @Path("/session/otp/create")
    public String createSessionWithOtp();

    /**
     * Terminates the current session and logs the user off.
     * <p>
     * This needs to be {@link Unsecured} to be able to log users off in case the
     * authentication provider is offline.
     */
    @POST
    @Path("/session/logout")
    @Unsecured
    public Response logout(@CookieParam(SessionManager.SESSION_COOKIE) Cookie session);

    /**
     * @return an authentication pack which can be used for build integrations and command line token authentication.
     * @deprecated Use {@link AuthAdminResource#getAuthPack(String, Boolean)} instead
     */
    @Deprecated(forRemoval = true, since = "7.8.0")
    @GET
    @Path("/auth-pack")
    @Produces(MediaType.TEXT_PLAIN)
    @RequiredPermission(permission = Permission.ADMIN)
    public String getAuthPack(@QueryParam("user") String user, @QueryParam("full") Boolean full);

    /**
     * @return an authentication pack of the current user
     */
    @GET
    @Path("/auth-pack-self")
    @Produces(MediaType.TEXT_PLAIN)
    public String getCurrentAuthPack(@QueryParam("full") Boolean full);

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
     * Retrieve the current user profile info.
     *
     * @return the currently logged in user's profile information.
     */
    @GET
    @Path("/user-profile")
    public UserProfileInfo getCurrentUserProfile();

    /**
     * Updates the current user with the given information.
     *
     * @param info the info for the current user.
     */
    @POST
    @Path("/user")
    public void updateCurrentUser(@CookieParam(SessionManager.SESSION_COOKIE) Cookie session, UserInfo info);

    /**
     * Removes the current user from the given group.
     *
     * @param groupId group's id
     */
    @DELETE
    @Path("/group/{groupId}")
    public void removeCurrentUserFromGroup(@PathParam("groupId") String groupId);

    /**
     * Deletes the current user. Does nothing if the current user is the last active global administrator.
     */
    @DELETE
    public void deleteCurrentUser(@CookieParam(SessionManager.SESSION_COOKIE) Cookie session);

    /**
     * Updates the current user's password.
     *
     * @param dto password data
     */
    @POST
    @Path("/change-password")
    public Response changePassword(UserChangePasswordDto dto);

    /**
     * @return the administrative interface for user managements.
     */
    @Path("/admin")
    public AuthAdminResource getAdmin();
}
