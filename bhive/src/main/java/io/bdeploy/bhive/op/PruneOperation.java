package io.bdeploy.bhive.op;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.AuditRecord.Severity;

/**
 * Removes dangling (unreferenced) objects from the {@link ObjectDatabase}.
 * <p>
 * Returns a map of removed {@link ObjectId}s along with the size of the removed
 * underlying file.
 */
public class PruneOperation extends BHive.Operation<SortedMap<ObjectId, Long>> {

    private static final Logger log = LoggerFactory.getLogger(PruneOperation.class);

    @Override
    public SortedMap<ObjectId, Long> call() throws Exception {
        SortedMap<ObjectId, Long> result = new TreeMap<>();

        AtomicLong max = new AtomicLong(-1);
        LongAdder current = new LongAdder();

        try (Activity activity = getActivityReporter().start("Prune (calculating)", () -> max.get(), () -> current.sum())) {
            // Wait for other operations locking the marker root (e.g. another prune).
            // The CreateObjectMarkersOperation and ClearObjectMarkersOperation will hold
            // off until the root is unlocked again, so:
            //  1) No NEW marker databases will be created and no concurrent prune operations will run.
            //  2) Existing transactions will be allowed to continue using their existing marker databases.
            //  3) Upon completion, existing trasactions will block removal of the markers until the root is unlocked.
            execute(new LockDirectoryOperation().setDirectory(getMarkerRoot()));

            SortedSet<ObjectId> all;
            try {
                // read existing manifests also inside the lock, so we are sure that the existing
                // manifests and objects are in a consistent state.
                Set<Manifest.Key> manifests = execute(new ManifestListOperation());
                Set<ObjectId> referenced;
                if (!manifests.isEmpty()) {
                    // we list all object, ignoring manifests which disappeared in the meantime (since the list call).
                    referenced = execute(new ObjectListOperation().addManifest(manifests).ignoreMissingManifest(true));
                } else {
                    referenced = new TreeSet<>();
                }

                SortedSet<ObjectId> orig = getObjectManager().db(db -> {
                    try {
                        return db.getAllObjects();
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e1);
                    }
                });
                all = new TreeSet<>(orig);
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

                List<ObjectId> auditList = new ArrayList<>();

                activity.activity("Prune (cleaning)");
                max.set(all.size());

                // delete within the lock, just to be sure that nobody "re-needs" one of the objects.
                for (ObjectId unreferenced : all) {
                    result.put(unreferenced, getObjectManager().db(x -> {
                        try {
                            long sz = x.getObjectSize(unreferenced);
                            x.removeObject(unreferenced);
                            if (auditList.size() < 50) {
                                auditList.add(unreferenced);
                            }
                            return sz;
                        } catch (NoSuchFileException e) {
                            log.debug("To-be-removed object is no longer existing: {}", unreferenced);
                            return (long) 0;
                        }
                    }));

                    current.increment();
                }

                getAuditor().audit(AuditRecord.Builder.fromSystem().setSeverity(Severity.NORMAL)
                        .setWhat(PruneOperation.class.getName()).setMessage("Removed " + result.size() + " Objects ")
                        .addParameter("removed", auditList.toString()).build());
            } finally {
                // Unlocking the root will allow:
                //  1) Ongoing operations to continue clearing their markers
                //  2) Other operations locking the root (e.g. another prune operation).
                execute(new ReleaseDirectoryLockOperation().setDirectory(getMarkerRoot()));
            }

            return result;
        }
    }

}
