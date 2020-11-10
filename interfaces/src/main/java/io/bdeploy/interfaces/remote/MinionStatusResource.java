package io.bdeploy.interfaces.remote;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
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

    /**
     * @param file the new logger config file.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void setLoggerConfig(java.nio.file.Path file);

    /**
     * @return a list of {@link RemoteDirectoryEntry}, can be used with
     *         {@link NodeDeploymentResource#getEntryContent(RemoteDirectoryEntry, long, long)}.
     */
    @GET
    @Path("/logs")
    public List<RemoteDirectoryEntry> getLogEntries();

}
