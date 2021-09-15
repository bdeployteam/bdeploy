package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/deployments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NodeDeploymentResource {

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
     * Deactivates a certain instance node manifest. This is used *only* when a node no longer takes part
     * in an instance, e.g. when the last application running on this node is removed.
     *
     * @param key the manifest to be deactivated.
     */
    @POST
    @Path("/deactivate")
    public void deactivate(Manifest.Key key);

    /**
     * @param key the key to be erased from the node.
     */
    @POST
    @Path("/remove")
    public void remove(Manifest.Key key);

    /**
     * Returns a the state of an instance.
     *
     * @return the state of the given instance.
     */
    @GET
    @Path("/state")
    public InstanceStateRecord getInstanceState(@QueryParam("i") String instanceId);

    /**
     * @param instanceId the instance UUID to fetch DATA directory content for
     * @return a list of the entries of the DATA directory.
     */
    @GET
    @Path("/dataDir")
    public List<RemoteDirectoryEntry> getDataDirectoryEntries(@QueryParam("u") String instanceId);

    /**
     * @param entry the entry to delete from the data directory.
     */
    @POST
    @Path("/dataDir/deleteEntry")
    public void deleteDataEntry(RemoteDirectoryEntry entry);

    /**
     * @param ports the ports to check whether they are open/used or not on the machine
     * @return a state for each port, true for 'used', false for 'free'.
     */
    @POST
    @Path("/check-ports")
    public Map<Integer, Boolean> getPortStates(List<Integer> ports);

}
