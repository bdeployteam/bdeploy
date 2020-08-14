package io.bdeploy.interfaces.remote;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.minion.MinionStatusDto;

/**
 * Query overall minion status.
 */
@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public interface MinionStatusResource {

    /**
     * @return the status of the minion.
     */
    @GET
    public MinionStatusDto getStatus();

}
