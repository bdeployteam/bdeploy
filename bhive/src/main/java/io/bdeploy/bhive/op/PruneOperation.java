package io.bdeploy.bhive.op;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Removes dangling (unreferenced) objects from the {@link ObjectDatabase}.
 * <p>
 * Returns a map of removed {@link ObjectId}s along with the size of the removed
 * underlying file.
 */
public class PruneOperation extends BHive.Operation<SortedMap<ObjectId, Long>> {

    @Override
    public SortedMap<ObjectId, Long> call() throws Exception {
        SortedMap<ObjectId, Long> result = new TreeMap<>();

        try (Activity activity = getActivityReporter().start("Pruning hive...", -1)) {
            SortedSet<Manifest.Key> manifests = execute(new ManifestListOperation());
            SortedSet<ObjectId> referenced;
            if (!manifests.isEmpty()) {
                ObjectListOperation listOp = new ObjectListOperation();
                manifests.forEach(listOp::addManifest);
                referenced = execute(listOp);
            } else {
                referenced = new TreeSet<>();
            }

            // Wait for other operations locking the marker root (e.g. another prune).
            // The CreateObjectMarkersOperation and ClearObjectMarkersOperation will hold
            // off until the root is unlocked again, so:
            //  1) No NEW marker databases will be created and no concurrent prune operations will run.
            //  2) Existing transactions will be allowed to continue using their existing marker databases.
            //  3) Upon completion, existing trasactions will block removal of the markers until the root is unlocked.
            MarkerDatabase.lockRoot(getMarkerRoot());

            SortedSet<ObjectId> orig = getObjectManager().db(ObjectDatabase::getAllObjects);
            SortedSet<ObjectId> all = new TreeSet<>(orig);
            all.removeAll(referenced);

            // read all existing marker databases and regard any existing object as referenced.
            try (DirectoryStream<Path> markerDbs = Files.newDirectoryStream(getMarkerRoot())) {
                for (Path markerDb : markerDbs) {
                    if (Files.isDirectory(markerDb)) {
                        MarkerDatabase mdb = new MarkerDatabase(markerDb, getActivityReporter());
                        all.removeAll(mdb.getAllObjects());
                    }
                }
            }

            // Unlocking the root will allow:
            //  1) Ongoing operations to continue clearing their markers
            //  2) Other operations locking the root (e.g. another prune operation).
            MarkerDatabase.unlockRoot(getMarkerRoot());

            for (ObjectId unreferenced : all) {
                result.put(unreferenced, getObjectManager().db(x -> {
                    long sz = x.getObjectSize(unreferenced);
                    x.removeObject(unreferenced);
                    return sz;
                }));
            }

            return result;
        }
    }

}
