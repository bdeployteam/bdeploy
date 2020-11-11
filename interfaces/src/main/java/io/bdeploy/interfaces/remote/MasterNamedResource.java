package io.bdeploy.interfaces.remote;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;

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
     *            <li>Have a label with the key 'X-Instance'. The
     *            value must be the UUID of the deployment this {@link Manifest}
     *            belongs to.
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
     * @param expectedTag the expected "current" tag of the instance to avoid conflicts
     */
    @POST
    @Path("/update")
    public Manifest.Key update(InstanceUpdateDto update, @QueryParam("e") String expectedTag);

    /**
     * @param instanceUuid the instance to delete
     */
    @DELETE
    @Path("/delete")
    public void delete(@QueryParam("u") String instanceUuid);

    /**
     * @param instanceUuid the instance to delete
     */
    @DELETE
    @Path("/deleteVersion")
    public void deleteVersion(@QueryParam("u") String instanceUuid, @QueryParam("t") String tag);

    /**
     * Create a new instance version by updating the underlying product to the given tag.
     *
     * @param uuid the UUID of the instance to update
     * @param productTag the tag of the product to update to.
     */
    @POST
    @Path("/updateTo")
    public void updateTo(@QueryParam("u") String uuid, @QueryParam("t") String productTag);

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
     * @param instanceId the instance UUID to fetch directory content for
     * @return a snapshot of the DATA directory for the given instance for each minion.
     */
    @GET
    @Path("/dataDir")
    public List<RemoteDirectory> getDataDirectorySnapshots(@QueryParam("u") String instanceId);

    /**
     * Delegates to the specified minion to receive a file.
     *
     * @see NodeDeploymentResource#getEntryContent(RemoteDirectoryEntry, long, long)
     */
    @POST
    @Path("/dataDir/entry")
    public EntryChunk getEntryContent(@QueryParam("m") String minion, RemoteDirectoryEntry entry, @QueryParam("o") long offset,
            @QueryParam("l") long limit);

    /**
     * @param minion the minion the entry refers to.
     * @param entry the entry to stream. The stream will include the complete content of the file.
     * @return an {@link InputStream} that can be used to stream the file.
     */
    @POST
    @Path("/dataDir/streamEntry")
    public Response getEntryStream(@QueryParam("m") String minion, RemoteDirectoryEntry entry);

    /**
     * @param minion the minion the entry refers to.
     * @param entry the actual entry to delete.
     */
    @POST
    @Path("/deleteDataEntry")
    public void deleteDataEntry(@QueryParam("m") String minion, RemoteDirectoryEntry entry);

    /**
     * @param instanceId the deployment/instance uuid
     * @param application the application id
     * @return the applications configuration
     */
    @GET
    @WeakTokenAllowed
    @Path("/client-config")
    public ClientApplicationConfiguration getClientConfiguration(@QueryParam("u") String instanceId,
            @QueryParam("a") String application);

    /**
     * @param instanceId the instance id to retrieve configuration data for
     * @return a ZIPed version of the configuration tree associated with the instance
     */
    @POST
    @WeakTokenAllowed
    @Path("/client-instance-config")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public java.nio.file.Path getClientInstanceConfiguration(Manifest.Key instanceId);

    /**
     * Starts all applications of the given instance having the start type 'INSTANCE' configured.
     *
     * @param instanceId
     */
    @POST
    @Path("/startAll")
    public void start(@QueryParam("u") String instanceId);

    /**
     * Starts a single application of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     */
    @POST
    @Path("/startApp")
    public void start(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId);

    /**
     * Stops a single application of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     */
    @POST
    @Path("/stopApp")
    public void stop(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId);

    /**
     * Stops all applications of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     */
    @POST
    @Path("/stopAll")
    public void stop(@QueryParam("u") String instanceId);

    /**
     * @param instanceId the unique id of the instance
     * @param tag the tag of the instance version to fetch for.
     * @param applicationId the unique id of the application to fetch the output entry for.
     * @return an {@link RemoteDirectory} specifying the minion the entry resides on. The {@link RemoteDirectoryEntry} list
     *         might be empty in case no output file exists.
     */
    @GET
    @Path("/output")
    public RemoteDirectory getOutputEntry(@QueryParam("u") String instanceId, @QueryParam("t") String tag,
            @QueryParam("a") String applicationId);

    /**
     * Returns status information about applications running in this instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @return the running applications
     */
    @GET
    @Path("/process-status")
    public InstanceStatusDto getStatus(@QueryParam("u") String instanceId);

    /**
     * Returns the full status of a single application.
     *
     * @param instanceId the unique id of the instance.
     * @param appUid the application UID to query
     * @return the full detailed status of the process.
     */
    @GET
    @Path("/process-details")
    public ProcessDetailDto getProcessDetails(@QueryParam("u") String instanceId, @QueryParam("a") String appUid);

    /**
     * @param principal the principal name to issue the token to.
     * @return a "weak" token only suitable for fetching by launcher-like applications.
     */
    @POST
    @Path("/weak-token")
    public String generateWeakToken(String principal);

    /**
     * Writes data to the stdin stream of an application.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     * @param data
     *            the data to write to stdin of the application.
     */
    @POST
    @Path("/stdin")
    public void writeToStdin(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId, String data);

    /**
     * @param minion the minion to check port availability on.
     * @param ports the ports to check whether they are open/used or not on the machine
     * @return a state for each port, true for 'used', false for 'free'.
     */
    @POST
    @Path("/check-ports")
    public Map<Integer, Boolean> getPortStates(@QueryParam("m") String minion, List<Integer> ports);

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
}
