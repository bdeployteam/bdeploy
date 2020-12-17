package io.bdeploy.bhive.remote.jersey;

import java.io.InputStream;
import java.util.Set;
import java.util.SortedMap;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;

/**
 * Resource allowing access to a single {@link BHive}.
 */
@Path("/hive")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface BHiveResource {

    /**
     * Retrieve all {@link Key}s along with the root tree {@link ObjectId} available
     * to the remote repository.
     *
     * @param names
     */
    @POST
    @WeakTokenAllowed
    @Path("/manifests")
    public SortedMap<Manifest.Key, ObjectId> getManifestInventory(String... names);

    /**
     * @param key the manifest to delete.
     */
    @POST
    @Path("/manifest_remove")
    @RequiredPermission(permission = Permission.WRITE)
    public void removeManifest(Manifest.Key key);

    /**
     * Perform a prune operation on the remote {@link BHive}, removing any unreferenced objects.
     */
    @POST
    @Path("/prune")
    public void prune();

    /**
     * From the given set, filter all remotely known {@link ObjectId}s and return only {@link ObjectId} which are not yet present
     * on the remote.
     */
    @POST
    @WeakTokenAllowed
    @Path("/obj_missing")
    public Set<ObjectId> getMissingObjects(Set<ObjectId> all);

    /**
     * Retrieve the {@link ObjectId} required to satisfy a given tree.
     */
    @POST
    @WeakTokenAllowed
    @Path("/tree_objects")
    public Set<ObjectId> getRequiredObjects(ObjectListSpec spec);

    /**
     * Retrieve the {@link ObjectId}s of all required {@link Tree} objects recursively in the given tree.
     */
    @POST
    @WeakTokenAllowed
    @Path("/tree_trees")
    public Set<ObjectId> getRequiredTrees(ObjectId tree);

    /**
     * Transfer the ZIPed {@link BHive} to the remote and apply all top-level
     * {@link Manifest}s referenced within.
     */
    @PUT
    @Path("/push")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @RequiredPermission(permission = Permission.WRITE)
    public void push(java.nio.file.Path zipedHive);

    /**
     * Streams manifests and objects into the remove hive.
     */
    @PUT
    @Path("/pushAsStream")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @RequiredPermission(permission = Permission.WRITE)
    public TransferStatistics pushAsStream(InputStream in);

    /**
     * Fetch manifests from the remote as ZIPed {@link BHive}.
     * <p>
     * The caller is responsible for cleaning up the file pointed at by the returned
     * {@link Path}.
     */
    @POST
    @WeakTokenAllowed
    @Path("/fetch")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public java.nio.file.Path fetch(FetchSpec spec);

    /**
     * Streams manifests and objects from the remove hive.
     */
    @POST
    @WeakTokenAllowed
    @Path("/fetchAsStream")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream fetchAsStream(FetchSpec spec);

    public static class FetchSpec {

        Set<ObjectId> requiredObjects;
        Set<Manifest.Key> manifestsToFetch;
    }

    public static class ObjectListSpec {

        Set<ObjectId> trees;
        Set<ObjectId> excludeTrees;
    }

}
