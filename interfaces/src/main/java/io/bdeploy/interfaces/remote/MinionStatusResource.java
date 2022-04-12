package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
    @WeakTokenAllowed
    public MinionStatusDto getStatus();

    /**
     * Repairs the default BHive, which is used for node operation and system data.
     *
     * @return a map of {@link ObjectId} to path strings within a Manifest. Those objects were damaged and removed.
     */
    @POST
    @Path("/maintenance/repair")
    public Map<String, String> repairDefaultBHive();

    /**
     * @return the amount of data cleaned from the BHive in bytes.
     */
    @POST
    @Path("/maintenance/prune")
    public long pruneDefaultBHive();

    /**
     * @param file the new logger config file.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void setLoggerConfig(java.nio.file.Path file);

    /**
     * Returns the contents of the log directory. If the optional 'hive' parameter is given, returns the contents of the named
     * hive's directory instead of the main server log directory.
     *
     * @return a list of {@link RemoteDirectoryEntry}, can be used with
     *         {@link CommonDirectoryEntryResource#getEntryContent(RemoteDirectoryEntry, long, long)}.
     */
    @GET
    @Path("/logs")
    public List<RemoteDirectoryEntry> getLogEntries(@QueryParam("h") String hive);
}
