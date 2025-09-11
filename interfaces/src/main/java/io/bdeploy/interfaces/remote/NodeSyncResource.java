package io.bdeploy.interfaces.remote;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/node-sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface NodeSyncResource {

    /**
     * Notifies this node that synchronization has finished from the master.
     */
    @HEAD
    @Path("/sync-finished")
    public void synchronizationFinished();

}
