package io.bdeploy.interfaces.remote;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.Version;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Perform updates on central
 * Mapped to "/master" as it replaces the MasterRooResource in 'central' mode.
 */
@Path("/master")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CommonUpdateResource {

    /**
     * Update the target server. The update must be already present on the server (BHive push).
     * <p>
     * This method plus the assumption that the {@link Manifest} has been pushed to the server already is the update protocol V1.
     *
     * @param version update to the given {@link Key}. The {@link Key} must have been pushed to the default hive before.
     * @param clean whether to clean up old versions.
     */
    @PUT
    @Path("/update")
    public void updateV1(Manifest.Key version, @QueryParam("clean") boolean clean);

    /**
     * @return the version of the update API. The client side must support all update API versions, the server side only the
     *         latest one. This implies that the client performing an update is always <b>at least</b> of the same version as the
     *         running server where the update is applied.
     */
    @GET
    @Path("/update-api-version")
    public Version getUpdateApiVersion();

    /**
     * Restarts the server without applying any changes to it.
     */
    @GET
    @Path("/restart")
    public void restartServer();

}
