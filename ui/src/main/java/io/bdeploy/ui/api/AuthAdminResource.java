package io.bdeploy.ui.api;

import java.util.List;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.UserInfo;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
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
     * @param name the name of the user to delete
     */
    @DELETE
    public void deleteUser(@QueryParam("name") String name);

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

}
