package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;

/**
 * Provides API to remote-update the master as well as the launcher software.
 */
@Path("/swup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareUpdateResource {

    @GET
    @Path("/bdeploy")
    public List<Manifest.Key> getBDeployVersions();

    /**
     * @return {@link NodeStatus} per node, may contain <code>null</code> values for offline nodes!
     */
    @GET
    @Path("/bdeploy/minions")
    public List<NodeStatus> getMinionNodes();

    @POST
    @Path("/selfUpdate")
    public void updateSelf(List<Manifest.Key> target);

    @GET
    @Path("/launcher")
    public List<Manifest.Key> getLauncherVersions();

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public List<Manifest.Key> uploadSoftware(@FormDataParam("file") InputStream inputStream);

    @POST // DELETE does not accept body for batch delete.
    public void deleteVersions(List<Manifest.Key> keys);

    @GET
    @Unsecured
    @Path("/download/{name : .+}/{tag}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadSoftware(@PathParam("name") String name, @PathParam("tag") String tag);

}
