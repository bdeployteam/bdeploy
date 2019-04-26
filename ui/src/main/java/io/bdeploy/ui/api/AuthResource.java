package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.CredentialsDto;

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
    public String authenticate(CredentialsDto credentials);

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

}
