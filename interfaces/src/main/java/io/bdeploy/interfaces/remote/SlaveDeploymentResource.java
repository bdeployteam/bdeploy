package io.bdeploy.interfaces.remote;

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
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;

@Path("/deployments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SlaveDeploymentResource {

    /**
     * @param key the key to be used to read the DeploymentManifest from.
     */
    @PUT
    public void install(Manifest.Key key);

    /**
     * @param key the manifest to be activated.
     */
    @POST
    public void activate(Manifest.Key key);

    /**
     * @param key the key to be erased from the slave.
     */
    @POST
    @Path("/remove")
    public void remove(Manifest.Key key);

    /**
     * @return lists of deployed manifests grouped by deployment UUID.
     */
    @GET
    @Path("/available")
    public SortedMap<String, SortedSet<Manifest.Key>> getAvailableDeployments();

    /**
     * @return lists of active deployed manifests by UUID.
     */
    @GET
    @Path("/active")
    public SortedMap<String, Manifest.Key> getActiveDeployments();

    /**
     * @param instanceId the instance UUID to fetch DATA directory content for
     * @return a list of the entries of the DATA directory.
     */
    @GET
    @Path("/dataDir")
    public List<InstanceDirectoryEntry> getDataDirectoryEntries(@QueryParam("u") String instanceId);

    /**
     * @param entry the {@link InstanceDirectoryEntry} to fetch content from.
     * @param offset the offset into the underlying file.
     * @param limit maximum bytes to read. 0 means no limit.
     * @return a chunk of the given entry, starting at offset until the <b>current</b> end of the file.
     */
    @POST
    @Path("/dataDir/entry")
    public EntryChunk getEntryContent(InstanceDirectoryEntry entry, @QueryParam("o") long offset, @QueryParam("l") long limit);

}
