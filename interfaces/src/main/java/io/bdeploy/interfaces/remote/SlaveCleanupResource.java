package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

@Path("/cleanup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SlaveCleanupResource {

    /**
     * @param toKeep {@link Set} of {@link Key}s to keep. Usually each {@link Key} should reference an
     *            {@link InstanceNodeManifest}, although there is no technical obligation to do so.
     * @return a {@link List} of {@link CleanupAction}s which are yet to be performed.
     */
    @POST
    @Path("/cleanup")
    public List<CleanupAction> cleanup(SortedSet<Manifest.Key> toKeep);

    /**
     * @param actions the actions to execute, obtained by calling {@link #cleanup(SortedSet)}.
     */
    @POST
    @Path("/perform")
    public void perform(List<CleanupAction> actions);

}
