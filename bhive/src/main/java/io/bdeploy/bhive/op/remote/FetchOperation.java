package io.bdeploy.bhive.op.remote;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ObjectExistsOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Fetches manifests from a remote {@link BHive} to the local {@link BHive}. If no
 * manifests are given, all remotely available manifests are fetched.
 */
public class FetchOperation extends RemoteOperation<TransferStatistics, FetchOperation> {

    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private String hiveName;

    @Override
    public TransferStatistics call() throws Exception {
        TransferStatistics stats = new TransferStatistics();
        assertNotNull(getRemote(), "Remote not set");

        SortedSet<Manifest.Key> toFetch = new TreeSet<>();
        SortedSet<ObjectId> toFetchRootTrees = new TreeSet<>();

        try (Activity activity = getActivityReporter().start("Fetching manifests...", -1)) {
            try (RemoteBHive rh = RemoteBHive.forService(getRemote(), hiveName, getActivityReporter())) {
                // is manifests are empty, the array will be empty, returning all manifests on the remote
                SortedMap<Manifest.Key, ObjectId> remoteManifests = rh
                        .getManifestInventory(manifests.stream().map(Manifest.Key::toString).toArray(String[]::new));

                if (manifests.isEmpty()) {
                    manifests.addAll(remoteManifests.keySet());
                }

                for (Manifest.Key key : manifests) {
                    if (!remoteManifests.containsKey(key)) {
                        throw new IllegalArgumentException("Manifest not found: " + key);
                    }
                    if (getManifestDatabase().hasManifest(key)) {
                        continue;
                    }
                    toFetch.add(key);
                    toFetchRootTrees.add(remoteManifests.get(key));
                }

                if (toFetch.isEmpty()) {
                    return stats;
                }

                stats.sumManifests = toFetch.size();

                // STEP 1: Figure out required trees for the roots to fetch
                SortedSet<ObjectId> requiredTrees = new TreeSet<>();
                toFetchRootTrees.forEach(t -> requiredTrees.addAll(rh.getRequiredTrees(t)));
                stats.sumTrees = requiredTrees.size();

                // STEP 2: Figure out which trees we already have locally.
                ObjectExistsOperation findExistingTrees = new ObjectExistsOperation();
                requiredTrees.forEach(findExistingTrees::addObject);
                SortedSet<ObjectId> treesWeAlreadyHave = execute(findExistingTrees);

                // STEP 3: Figure out the trees which we /do/ need to fetch
                SortedSet<ObjectId> missingTrees = new TreeSet<>(requiredTrees);
                missingTrees.removeAll(treesWeAlreadyHave);
                stats.sumMissingTrees = missingTrees.size();

                // STEP 4: Find objects for all missing objects, filtering trees we have.
                SortedSet<ObjectId> requiredObjects = rh.getRequiredObjects(missingTrees, treesWeAlreadyHave);

                // STEP 5: from these objects check which are existing locally
                ObjectExistsOperation findExistingObjects = new ObjectExistsOperation();
                requiredObjects.forEach(findExistingObjects::addObject);
                SortedSet<ObjectId> existingObjects = execute(findExistingObjects);

                // STEP 6 calculate which objects are missing from the required and existing ones
                SortedSet<ObjectId> missingObjects = new TreeSet<>(requiredObjects);
                missingObjects.removeAll(existingObjects);
                stats.sumMissingObjects = missingObjects.size();

                // STEP 7: fetch from the remote all required objects and manifests.
                Path z = rh.fetch(missingObjects, toFetch);
                stats.transferSize = Files.size(z);
                try (BHive zHive = new BHive(UriBuilder.fromUri("jar:" + z.toUri()).build(), getActivityReporter())) {
                    zHive.execute(new CopyOperation().setDestinationHive(this).setPartialAllowed(false));
                } finally {
                    Files.deleteIfExists(z);
                }
            }
        }
        return stats;
    }

    public FetchOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public FetchOperation setHiveName(String name) {
        hiveName = name;
        return this;
    }

}