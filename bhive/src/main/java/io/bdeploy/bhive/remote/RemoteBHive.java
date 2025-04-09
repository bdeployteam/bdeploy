package io.bdeploy.bhive.remote;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.ZipHelper;

/**
 * Represents a possibly remote (might also be "remote" in the sense of another directory on disc) BHive. This interface is *not*
 * meant to be used for direct remote interface implementation, but rather is a logical representation of a {@link BHive} which is
 * not the currently operated-on {@link BHive}.
 *
 * @see LocalBHiveAdapter
 * @see JerseyRemoteBHive
 */
public interface RemoteBHive extends AutoCloseable {

    /**
     * From the given set, filter all remotely known {@link ObjectId}s and return only {@link ObjectId} which are not yet present
     * on the remote.
     */
    public Set<ObjectId> getMissingObjects(Set<ObjectId> all);

    /**
     * Retrieve all {@link Key}s along with the root tree {@link ObjectId} available
     * to the remote repository.
     *
     * @param names a possibly empty list of named to restrict to. Each can be any number of full (!) name segments
     *            (/-separated).
     */
    public SortedMap<Manifest.Key, ObjectId> getManifestInventory(String... names);

    /**
     * @param key the manifest to delete
     */
    public void removeManifest(Manifest.Key key);

    /**
     * Perform a prune operation on the remote {@link BHive}, removing any unreferenced objects.
     */
    public void prune();

    /**
     * Retrieve the {@link ObjectId}s required to satisfy a given tree.
     */
    public Set<ObjectId> getRequiredObjects(Set<ObjectId> trees, Set<ObjectId> excludeTrees);

    /**
     * Retrieve the {@link ObjectId}s of all required {@link Tree} objects recursively in the given tree. The returned set is
     * ordered so that child-trees are first followed by their parents.
     */
    public Set<ObjectId> getRequiredTrees(ObjectId tree);

    /**
     * Transfer the ZIPed {@link BHive} to the remote and apply all top-level
     * {@link Manifest}s referenced within.
     */
    public void push(Path zipedHive);

    /**
     * Streams objects directly into the given remote hive.
     */
    public TransferStatistics pushAsStream(InputStream in);

    /**
     * Fetch manifests from the remote as ZIPed {@link BHive}. Only objects in the given requiredObjects are included.
     */
    public Path fetch(Set<ObjectId> objects, Set<Manifest.Key> manifests);

    /**
     * Streams the given objects one after each other to the given output stream.
     */
    public InputStream fetchAsStream(Set<ObjectId> objects, Set<Key> manifests);

    /**
     * Figures out the type of {@link RemoteBHive} required for the given
     * {@link RemoteService} and returns an instance.
     * <p>
     * The returned {@link RemoteBHive} is not guaranteed to be remote in the sense
     * of network. It might be an adapter to another local {@link BHive} in the
     * filesystem.
     * <p>
     * In case the hive is remote, the name is used to identify the hive on the
     * server, as the server might serve multiple hives.
     */
    public static RemoteBHive forService(RemoteService svc, String name, ActivityReporter reporter) {
        switch (svc.getUri().getScheme().toLowerCase()) {
            case "file":
            case "jar":
                if (!ZipHelper.isZipUri(svc.getUri())) {
                    // in case we're trying to go local, the BHive must exist and be initialized.
                    // we do this with the same means as the CLI to align with it.
                    Path p = Paths.get(svc.getUri());
                    if (!new ExistingPathValidator().test(p.toString())) {
                        throw new IllegalArgumentException("Target local BHive does not exist.");
                    }
                    if (!new ExistingPathValidator().test(p.resolve("objects").toString())) {
                        throw new IllegalArgumentException("Target local BHive does not seem to be an initialized BHive.");
                    }
                    if (!new PathOwnershipValidator().test(p.toString())) {
                        throw new IllegalArgumentException("Target local BHive is not owned by the current user.");
                    }
                }
                return new LocalBHiveAdapter(new BHive(svc.getUri(), null, reporter));
            case "https":
                return new JerseyRemoteBHive(svc, name);
            default:
                throw new UnsupportedOperationException("scheme " + svc.getUri().getScheme() + " not supported");
        }
    }

    @Override
    public void close();
}
