package io.bdeploy.interfaces.remote;

import java.util.List;

import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.minion.MinionMonitoringDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
     *         {@link CommonDirectoryEntryResource#getEntryContent(RemoteDirectoryEntry, long, long)}.
     */
    @GET
    @Path("/logs")
    public List<RemoteDirectoryEntry> getLogEntries();

    /**
     * Get updated monitoring information based on persisted information in
     * MinionManifest
     *
     * @param update true if the MinionManifest should be updated, false otherwise
     * @return the updated monitoring information
     */
    @GET
    @Path("/monitoring")
    public MinionMonitoringDto getMonitoring(@QueryParam("u") boolean update);
}
