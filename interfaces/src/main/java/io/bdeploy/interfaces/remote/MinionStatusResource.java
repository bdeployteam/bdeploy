package io.bdeploy.interfaces.remote;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
