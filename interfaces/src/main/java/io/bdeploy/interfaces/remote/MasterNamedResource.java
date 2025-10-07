package io.bdeploy.interfaces.remote;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.interfaces.VerifyOperationResultDto;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.BulkPortStatesDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Manages a certain hive on the master
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterNamedResource {

    /**
     * @param key the key to a master {@link Manifest} (grouping deployment
     *            manifests for each available node). The master {@link Manifest}
     *            <b>must</b>:
     *            <ul>
     *            <li>Have a root {@link Tree} containing of entries which are all
     *            {@link Manifest} references. Each Entries name is the name of the
     *            node as known to the master to install to, the {@link Manifest}
     *            reference references a {@link Manifest} suitable for the DCU.
     *            <li>Have a label with the key 'X-Instance'. The value must be the
     *            ID of the instance this {@link Manifest} belongs to.
     *            </ul>
     */
    @PUT
    public void install(Manifest.Key key);

    /**
     * @param key activates the previously installed master manifest.
     * @see #install(io.bdeploy.bhive.model.Manifest.Key)
     */
    @POST
    public void activate(Manifest.Key key);

    /**
     * @param key the installation to remove.
     * @see #install(io.bdeploy.bhive.model.Manifest.Key)
     */
    @POST
    @Path("/remove")
    public void uninstall(Manifest.Key key);

    /**
     * @param update the state of the instance to write
     * @param expectedTag the expected "current" tag of the instance to avoid
     *            conflicts
     */
    @POST
    @Path("/update")
    public Manifest.Key update(InstanceUpdateDto update, @QueryParam("e") String expectedTag);

    /**
     * @param instanceId the instance to delete
     */
    @DELETE
    @Path("/delete")
    public void delete(@QueryParam("u") String instanceId);

    /**
     * @param instanceId the instance to delete
     */
    @DELETE
    @Path("/deleteVersion")
    public void deleteVersion(@QueryParam("u") String instanceId, @QueryParam("t") String tag);

    /**
     * Fetches the persistent state of a single instance.
     *
     * @param instance the instance to query state for.
     * @return the state of a single instance.
     */
    @GET
    @Path("/state")
    public InstanceStateRecord getInstanceState(@QueryParam("i") String instance);

    /**
     * @param instanceId the instance ID to fetch the DATA directory content for
     * @return a snapshot of the DATA directory for the given instance for each minion.
     */
    @GET
    @Path("/dataDir")
    public List<RemoteDirectory> getDataDirectorySnapshots(@QueryParam("u") String instanceId);

    /**
     * @param instanceId the instance ID to fetch the LOG_DATA directory content for
     * @return a snapshot of the LOG_DATA directory for the given instance for each minion.
     */
    @GET
    @Path("/logDataDir")
    public List<RemoteDirectory> getLogDataDirectorySnapshots(@QueryParam("u") String instanceId);

    /**
     * Delegates to the specified minion to receive a file.
     *
     * @see CommonDirectoryEntryResource#getEntryContent(RemoteDirectoryEntry, long, long)
     */
    @POST
    @Path("/dataDir/entry")
    public EntryChunk getEntryContent(@QueryParam("m") String minion, RemoteDirectoryEntry entry, @QueryParam("o") long offset,
            @QueryParam("l") long limit);

    /**
     * @param minion the minion the entry refers to.
     * @param entry the entry to stream. The stream will include the complete
     *            content of the file.
     * @return an {@link InputStream} that can be used to stream the file.
     */
    @POST
    @Path("/dataDir/streamEntry")
    public Response getEntryStream(@QueryParam("m") String minion, RemoteDirectoryEntry entry);

    /**
     * @param minion the minion the entries refer to.
     * @param entries the requested entries to stream.
     * @return an {@link InputStream} that can be used to stream a ZIP file containing all entries.
     */
    @POST
    @Path("/dataDir/streamZipEntries")
    public Response getEntriesZipSteam(@QueryParam("m") String minion, List<RemoteDirectoryEntry> entries);

    /**
     * Add/edit/delete files in the DATA directory.
     */
    @PUT
    @Path("/dataDir/updateEntries")
    public void updateDataEntries(@QueryParam("u") String instanceId, @QueryParam("m") String minion,
            List<FileStatusDto> updates);

    /**
     * @param minion the minion the entry refers to.
     * @param entry the actual entry to delete.
     */
    @POST
    @Path("/deleteDataEntry")
    public void deleteDataEntry(@QueryParam("m") String minion, RemoteDirectoryEntry entry);

    /**
     * @param instanceId the instance ID
     * @param application the application id
     * @return the applications configuration
     */
    @GET
    @WeakTokenAllowed
    @Path("/client-config")
    public ClientApplicationConfiguration getClientConfiguration(@QueryParam("u") String instanceId,
            @QueryParam("a") String application);

    /**
     * @param instanceId the instance ID
     * @param application the application id
     */
    @GET
    @WeakTokenAllowed
    @Path("/client-start")
    public void logClientStart(@QueryParam("u") String instanceId, @QueryParam("a") String application,
            @QueryParam("h") String hostname);

    /**
     * Starts all applications of the given instance having the start type
     * 'INSTANCE' configured.
     *
     * @param instanceId
     */
    @POST
    @Path("/startAll")
    public void start(@QueryParam("u") String instanceId);

    /**
     * Starts one or more applications, respecting potential process control groups.
     *
     * @param instanceId the instance ID
     * @param applicationIds the applications to start
     * @since 4.2.0
     */
    @POST
    @Path("/startApps")
    public void start(@QueryParam("u") String instanceId, List<String> applicationIds);

    /**
     * Stops one or more applications of an instance, respecting potential process control groups.
     *
     * @param instanceId the unique id of the instance.
     * @param applicationIds the applications to stop.
     * @since 4.2.0
     */
    @POST
    @Path("/stopApps")
    public void stop(@QueryParam("u") String instanceId, List<String> applicationIds);

    /**
     * Stops all applications of an instance.
     *
     * @param instanceId the unique id of the instance.
     */
    @POST
    @Path("/stopAll")
    public void stop(@QueryParam("u") String instanceId);

    /**
     * @param instanceId the unique id of the instance
     * @param tag the tag of the instance version to fetch for.
     * @param applicationId the unique id of the application to fetch the output
     *            entry for.
     * @return an {@link RemoteDirectory} specifying the minion the entry resides
     *         on. The {@link RemoteDirectoryEntry} list might be empty in case no
     *         output file exists.
     */
    @GET
    @Path("/output")
    public RemoteDirectory getOutputEntry(@QueryParam("u") String instanceId, @QueryParam("t") String tag,
            @QueryParam("a") String applicationId);

    /**
     * Returns status information about applications running in this instance.
     *
     * @param instanceId the unique id of the instance.
     * @return the running applications
     */
    @GET
    @Path("/process-status")
    public InstanceStatusDto getStatus(@QueryParam("u") String instanceId);

    /**
     * Returns the full status of a single application.
     *
     * @param instanceId the unique id of the instance.
     * @param appId the application UID to query
     * @return the full detailed status of the process.
     * @deprecated this method is inherently slow as it needs to determine the node the process is running on.
     */
    @Deprecated(since = "5.6.0", forRemoval = true)
    @GET
    @Path("/process-details")
    public ProcessDetailDto getProcessDetails(@QueryParam("u") String instanceId, @QueryParam("a") String appId);

    /**
     * Returns the full status of a single application.
     *
     * @param instanceId the unique ID of the instance
     * @param appId the application ID to query
     * @param node the name of the node the application is expected to run on.
     * @return the full detailed status of the process.
     */
    @GET
    @Path("/process-details-fast")
    public ProcessDetailDto getProcessDetailsFromNode(@QueryParam("u") String instanceId, @QueryParam("a") String appId,
            @QueryParam("n") String node);

    /**
     * @param principal the principal name to issue the token to.
     * @return a "weak" token only suitable for fetching by launcher-like
     *         applications.
     */
    @POST
    @Path("/weak-token")
    public String generateWeakToken(String principal);

    /**
     * Writes data to the stdin stream of an application.
     *
     * @param instanceId the unique id of the instance.
     * @param applicationId the unique ID of the application.
     * @param node the SERVER node on which to run. If app is running on a MULTI node this parameter is required. Otherwise this is optional.
     * @param data the data to write to stdin of the application.
     */
    @POST
    @Path("/stdin")
    public void writeToStdin(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId, @QueryParam("n") String node, String data);

    /**
     * @param minion the minion to check port availability on.
     * @param ports the ports to check whether they are open/used or not on the
     *            machine
     * @return a state for each port, true for 'used', false for 'free'.
     */
    @POST
    @Path("/check-ports")
    public Map<Integer, Boolean> getPortStates(@QueryParam("m") String minion, List<Integer> ports);

    /**
     * @param node2ports a map of ports to node, so that you can bulk request port states
     * @return a state for each port, true for 'used', false for 'free'.
     */
    @POST
    @Path("/check-ports-bulk")
    public BulkPortStatesDto getPortStatesBulk(Map<String, List<Integer>> node2ports);

    /**
     * Loads all runtime events from the minions
     */
    @GET
    @Path("/runtimeHistory")
    public MasterRuntimeHistoryDto getRuntimeHistory(@QueryParam("u") String instanceId);

    /**
     * Returns the instance banner configuration.
     *
     * @param instanceId the unique id of the instance.
     * @return the instance banner configuration.
     */
    @GET
    @Path("/banner")
    public InstanceBannerRecord getBanner(@QueryParam("u") String instanceId);

    /**
     * Updates the instance banner configuration.
     *
     * @param instanceId the unique id of the instance.
     * @param instanceBannerRecord the new banner configuration
     */
    @POST
    @Path("/banner")
    public void updateBanner(@QueryParam("u") String instanceId, InstanceBannerRecord instanceBannerRecord);

    /**
     * Returns the instance attributes.
     *
     * @param instanceId the unique id of the instance.
     * @return the instance attributes.
     */
    @GET
    @Path("/attributes")
    public CustomAttributesRecord getAttributes(@QueryParam("u") String instanceId);

    /**
     * Updates the instance attributes.
     *
     * @param instanceId the unique id of the instance.
     * @param attributes the new attributes
     */
    @POST
    @Path("/attributes")
    public void updateAttributes(@QueryParam("u") String instanceId, CustomAttributesRecord attributes);

    /**
     * Updates each instances recorded overall status in the instance group's BHive.
     */
    @GET
    @Path("/overallStatus")
    public void updateOverallStatus();

    /**
     * @param instanceId the instance's ID
     * @param application the client application's ID
     * @return an {@link InputStream} that can be used to stream a ZIP file containing all entries.
     */
    @POST
    @WeakTokenAllowed
    @Path("/launcher/streamZipConfig")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getConfigZipSteam(@QueryParam("u") String instanceId, @QueryParam("a") String application);

    /**
     * @return the {@link MasterSystemResource} which can be used to manage systems in this instance group.
     */
    @Path("/systems")
    public MasterSystemResource getSystemResource();

    /**
     * Verifies application of an instance.
     *
     * @param instanceId the unique id of the instance.
     * @param appId the unique id of the application
     * @param node the SERVER node on which to run. If app is running on a MULTI node this parameter is required. Otherwise this is optional.
     */
    @POST
    @Path("/verify")
    public VerifyOperationResultDto verify(@QueryParam("u") String instanceId, @QueryParam("a") String appId, @QueryParam("n") String node);

    /**
     * Reinstalls application of an instance.
     *
     * @param instanceId the unique id of the instance.
     * @param appId the unique id of the application
     * @param node the SERVER node on which to run. If app is running on a MULTI node this parameter is required. Otherwise this is optional.
     */
    @POST
    @Path("/reinstall")
    public void reinstall(@QueryParam("u") String instanceId, @QueryParam("a") String appId, @QueryParam("n") String node);

    /**
     * Synchronizes instance for master and node.
     * Makes sure every instance version installed on master that includes the node is installed on the node as well
     * Makes sure node's active version is not outdated
     *
     * @param nodeName - name of the node that will be synchronized with master
     * @param instanceId - id of the instance that will be synchronized
     */
    @POST
    @Path("/sync-node")
    public void syncNode(@QueryParam("nodeName") String nodeName, @QueryParam("instanceId") String instanceId);
}
