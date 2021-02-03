package io.bdeploy.bhive.op.remote;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ObjectExistsOperation;
import io.bdeploy.bhive.op.ObjectExistsOperation.Result;
import io.bdeploy.bhive.op.ObjectReadOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter.Activity;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Fetches manifests from a remote {@link BHive} to the local {@link BHive}. If no
 * manifests are given, all remotely available manifests are fetched.
 */
public class FetchOperation extends TransactedRemoteOperation<TransferStatistics, FetchOperation> {

    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private String hiveName;

    @Override
    public TransferStatistics callTransacted() throws Exception {
        TransferStatistics stats = new TransferStatistics();
        assertNotNull(getRemote(), "Remote not set");

        SortedSet<Manifest.Key> requiredManifests = new TreeSet<>();
        SortedSet<ObjectId> toFetchRootTrees = new TreeSet<>();

        Instant start = Instant.now();
        try (Activity activity = getActivityReporter().start("Fetching manifests...", -1)) {
            try (RemoteBHive rh = RemoteBHive.forService(getRemote(), hiveName, getActivityReporter())) {
                // is manifests are empty, the array will be empty, returning all manifests on the remote
                String[] manifestsAsArray = manifests.stream().map(Manifest.Key::toString).toArray(String[]::new);
                SortedMap<Manifest.Key, ObjectId> manifest2Tree = rh.getManifestInventory(manifestsAsArray);
                if (manifests.isEmpty()) {
                    manifests.addAll(manifest2Tree.keySet());
                }

                for (Manifest.Key key : manifests) {
                    if (!manifest2Tree.containsKey(key)) {
                        throw new IllegalArgumentException("Manifest not found: " + key);
                    }
                    if (getManifestDatabase().hasManifest(key)) {
                        continue;
                    }
                    requiredManifests.add(key);
                    toFetchRootTrees.add(manifest2Tree.get(key));
                }
                if (requiredManifests.isEmpty()) {
                    return stats;
                }

                // STEP 1: Figure out required trees for the roots to fetch
                Set<ObjectId> requiredTrees = new LinkedHashSet<>();
                toFetchRootTrees.forEach(t -> requiredTrees.addAll(rh.getRequiredTrees(t)));

                // STEP 2: Figure out which trees we already have locally.
                Result treeResult = execute(new ObjectExistsOperation().addAll(requiredTrees));

                // STEP 3: Find objects for all missing objects, filtering trees we have.
                Set<ObjectId> requiredObjects = new LinkedHashSet<>();
                if (!treeResult.missing.isEmpty()) {
                    requiredObjects = rh.getRequiredObjects(treeResult.missing, treeResult.existing);
                }

                // STEP 4: Check which of the required objects we already have
                Result objectResult = execute(new ObjectExistsOperation().addAll(requiredObjects));

                // STEP 5: Fetch from the remote all required objects and manifests.
                TransferStatistics fetchStats = fetch(rh, objectResult.missing, requiredManifests);

                // Update statistics with some new knowledge. the fetch call can only know a few numbers,
                // as for instance the number of trees is irrelevant during actual operation.
                stats.transferSize = fetchStats.transferSize;
                stats.sumManifests = fetchStats.sumManifests;
                stats.sumMissingObjects = fetchStats.sumMissingObjects;
                stats.sumTrees = requiredTrees.size();
                stats.sumMissingTrees = treeResult.missing.size();
            }
        } finally {
            stats.duration = Duration.between(start, Instant.now()).toMillis();
        }
        return stats;
    }

    public FetchOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public FetchOperation addManifest(Collection<Manifest.Key> keys) {
        manifests.addAll(keys);
        return this;
    }

    public FetchOperation setHiveName(String name) {
        hiveName = name;
        return this;
    }

    public SortedSet<Manifest.Key> getManifests() {
        return manifests;
    }

    private TransferStatistics fetch(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) throws IOException {
        try {
            return fetchAsStream(rh, objects, manifests);
        } catch (UnsupportedOperationException ex) {
            return fetchAsZip(rh, objects, manifests);
        }
    }

    private TransferStatistics fetchAsZip(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) throws IOException {
        Path z = rh.fetch(objects, manifests);
        try (BHive zHive = new BHive(UriBuilder.fromUri("jar:" + z.toUri()).build(), getActivityReporter())) {
            TransferStatistics t = zHive.execute(new CopyOperation().setDestinationHive(this).setPartialAllowed(false));
            t.transferSize = Files.size(z); // transferred size != actual size.
            return t;
        } finally {
            Files.deleteIfExists(z);
        }
    }

    private TransferStatistics fetchAsStream(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) {
        InputStream stream = rh.fetchAsStream(objects, manifests);
        return execute(new ObjectReadOperation().stream(stream));
    }

}