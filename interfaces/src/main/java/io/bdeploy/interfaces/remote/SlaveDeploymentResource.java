package io.bdeploy.interfaces.remote;

import java.util.SortedMap;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;

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

}
