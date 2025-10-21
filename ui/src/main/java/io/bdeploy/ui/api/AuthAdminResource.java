package io.bdeploy.ui.api;

import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
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

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredPermission(permission = Permission.ADMIN)
public interface AuthAdminResource {

    /**
     * @param info the user to create
     */
    @PUT
    @Path("/local")
    public void createLocalUser(UserInfo info);

    /**
     * @param user the user to manipulate
     * @param password the new password to set
     */
    @POST
    @Path("/local/pw")
    public void updateLocalUserPassword(@QueryParam("user") String user, String password);

    /**
     * @param info the new user information, password is ignored
     */
    @POST
    public void updateUser(UserInfo info);

    /**
     * Batched call to {@link #updateUser(UserInfo)}
     */
    @POST
    @Path("/users")
    public void updateUsers(List<UserInfo> infos);

    /**
     * Deletes the user with the given name. Never deletes the last active global administrator.
     *
     * @param name the name of the user to delete
     * @return <code>true</code> if the user was deleted, else <code>false</code>
     */
    @DELETE
    public boolean deleteUser(@QueryParam("name") String name);

    /**
     * @param name the user to load
     * @return the user's full information
     */
    @GET
    public UserInfo getUser(@QueryParam("name") String name);

    /**
     * @return a list of all known user names.
     */
    @GET
    @Path("/names")
    public SortedSet<String> getAllUserNames();

    /**
     * @return a list of all known user.
     */
    @GET
    @Path("/users")
    public SortedSet<UserInfo> getAllUser();

    /**
     * @return a list of all known user groups.
     */
    @GET
    @Path("/user-groups")
    public SortedSet<UserGroupInfo> getAllUserGroups();

    /**
     * @param info the user group to create
     */
    @PUT
    @Path("/user-groups")
    public void createUserGroup(UserGroupInfo info);

    /**
     * @param info the new user group information
     */
    @POST
    @Path("/user-groups")
    public void updateUserGroup(UserGroupInfo info);

    /**
     * @param id the id of the user group to delete
     */
    @DELETE
    @Path("/user-groups/{group}")
    public void deleteUserGroups(@PathParam("group") String id);

    /**
     * @param group group's id
     * @param user user's name
     */
    @POST
    @Path("/user-groups/{group}/users/{user}")
    public void addUserToGroup(@PathParam("group") String group, @PathParam("user") String user);

    /**
     * @param group group's id
     * @param user user's name
     */
    @DELETE
    @Path("/user-groups/{group}/users/{user}")
    public void removeUserFromGroup(@PathParam("group") String group, @PathParam("user") String user);

    @GET
    @Path("/new-uuid")
    public String createId();

    @POST
    @Path("/traceAuthentication")
    public List<String> traceAuthentication(CredentialsApi credentials);

    @POST
    @Path("/testLdapServer")
    public String testLdapServer(LDAPSettingsDto dto);

    @POST
    @Path("/import-ldap-accounts")
    public String importAccountsLdapServer(LDAPSettingsDto dto);

    @Path("/user-bulk")
    public UserBulkResource getUserBulkResource();

    @Path("/user-group-bulk")
    public UserGroupBulkResource getUserGroupBulkResource();
}
