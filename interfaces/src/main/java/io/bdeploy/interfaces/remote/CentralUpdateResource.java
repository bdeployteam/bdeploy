package io.bdeploy.interfaces.remote;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;

/**
 * Perform updates on central
 * Mapped to "/master" as it replaces the MasterRooResource in 'central' mode.
 */
@Path("/master")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CentralUpdateResource {

    /**
     * Update the central server
     *
     * @param version update to the given {@link Key}. The {@link Key} must have been pushed to the default hive before.
     * @param clean whether to clean up old versions.
     */
    @PUT
    @Path("/update")
    public void update(Manifest.Key version, @QueryParam("clean") boolean clean);

}
