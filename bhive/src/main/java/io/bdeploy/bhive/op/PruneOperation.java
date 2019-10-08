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

            // TODO: this might be /very/ memory intensive.
            SortedSet<ObjectId> orig = getObjectManager().db(ObjectDatabase::getAllObjects);
            SortedSet<ObjectId> all = new TreeSet<>(orig);
            all.removeAll(referenced);

            // read all existing marker databases and regard any existing object as referenced.
            try (DirectoryStream<Path> markerDbs = Files.newDirectoryStream(getMarkerRoot())) {
                for (Path markerDb : markerDbs) {
                    MarkerDatabase mdb = new MarkerDatabase(markerDb, getActivityReporter());
                    all.removeAll(mdb.getAllObjects());
                }
            }

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
