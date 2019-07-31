package io.bdeploy.interfaces.remote;

import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
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
    public void remove(Manifest.Key key);

    /**
     * Returns all activated deployments of all instance groups of this hive.
     *
     * @return return active deployments. Key=Unique ID of the instance group. Value=The activated version
     */
    @GET
    @Path("/active")
    public SortedMap<String, Manifest.Key> getActiveDeployments();

    /**
     * @return available manifests to deploy on a certain minion for a certain instance.
     */
    @GET
    @Path("/available-m")
    public SortedSet<Manifest.Key> getAvailableDeploymentsOfMinion(@QueryParam("m") String minion,
            @QueryParam("i") String instance);

    /**
     * @return return active deployments on a certain minion.
     */
    @GET
    @Path("/active-m")
    public SortedMap<String, Manifest.Key> getActiveDeploymentsOfMinion(@QueryParam("m") String minion);

    /**
     * @return available manifests to deploy for the given instance.
     */
    @GET
    @Path("/available-i")
    public SortedSet<Manifest.Key> getAvailableDeploymentsOfInstance(@QueryParam("i") String instance);

    /**
     * @param instanceId the instance UUID to fetch directory content for
     * @return a snapshot of the DATA directory for the given instance for each minion.
     */
    @GET
    @Path("/dataDir")
    public List<InstanceDirectory> getDataDirectorySnapshots(@QueryParam("u") String instanceId);

    /**
     * Delegates to the specified minion to receive a file.
     *
     * @see SlaveDeploymentResource#getEntryContent(InstanceDirectoryEntry, long, long)
     */
    @POST
    @Path("/dataDir/entry")
    public EntryChunk getEntryContent(@QueryParam("m") String minion, InstanceDirectoryEntry entry, @QueryParam("o") long offset,
            @QueryParam("l") long limit);

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
     * @return an {@link InstanceDirectory} specifying the minion the entry resides on. The {@link InstanceDirectoryEntry} list
     *         might be empty in case no output file exists.
     */
    @GET
    @Path("/output")
    public InstanceDirectory getOutputEntry(@QueryParam("u") String instanceId, @QueryParam("t") String tag,
            @QueryParam("a") String applicationId);

    /**
     * Returns status information about applications running in this instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @return the running applications
     */
    @GET
    @Path("/status")
    public InstanceStatusDto getStatus(@QueryParam("u") String instanceId);

    /**
     * @param principal the principal name to issue the token to.
     * @return a "weak" token only suitable for fetching by launcher-like applications.
     */
    @POST
    @Path("/weak-token")
    public String generateWeakToken(String principal);

    /**
     * Returns a list of instance configurations available in this hive.
     *
     * @return the list of instance configurations.
     */
    @GET
    @Path("/instances")
    public Collection<InstanceConfiguration> listInstanceConfigurations();

}
