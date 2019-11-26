package io.bdeploy.bhive.op;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;

/**
 * Checks for missing and corrupt objects. Missing objects will lead to an
 * exception, as they are required for full tree traversal. Corrupted objects
 * will be collected, and returned.
 */
public class ObjectConsistencyCheckOperation extends BHive.Operation<Set<ElementView>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> roots = new TreeSet<>();
    private boolean dryRun = true;

    @Override
    public Set<ElementView> call() throws Exception {
        if (roots.isEmpty()) {
            SortedSet<Manifest.Key> localManifests = execute(new ManifestListOperation());
            roots.addAll(localManifests);
        }

        Activity scanning = getActivityReporter().start("Scanning manifest trees...", roots.size());

        Path markerPath = Files.createTempDirectory("markers-");
        MarkerDatabase markerDb = new MarkerDatabase(markerPath, getActivityReporter());
        Set<ElementView> broken = new TreeSet<>();
        try {
            for (Manifest.Key key : roots) {
                List<ElementView> existingElements = new ArrayList<>();
                if (!execute(new ManifestExistsOperation().setManifest(key))) {
                    // does not even exist - happens if manifest consistency operation removed it.
                    continue;
                }

                TreeView state = execute(new ScanOperation().setManifest(key));

                if (state.getElementId() == null) {
                    // well, well - pretty damaged.
                    broken.add(state.getChildren().values().iterator().next());
                    continue;
                }

                state.visit(new TreeVisitor.Builder().onBlob(existingElements::add).onTree(existingElements::add)
                        .onManifestRef(existingElements::add).build());

                broken.addAll(checkElements(markerDb, existingElements));

                scanning.workAndCancelIfRequested(1);
            }
        } finally {
            scanning.done();
            PathHelper.deleteRecursive(markerPath);
        }

        return broken;
    }

    private Set<ElementView> checkElements(MarkerDatabase markerDb, List<ElementView> existingElements) {
        Set<ElementView> broken = new TreeSet<>();
        for (ElementView obj : existingElements) {
            if (markerDb.hasObject(obj.getElementId())) {
                continue;
            } else {
                // write the marker ourselves, the content never matches the checksum (intentionally).
                markerDb.addMarker(obj.getElementId());
            }

            if (!getObjectManager().checkObject(obj.getElementId(), !dryRun)) {
                broken.add(obj);
            }
            if (obj instanceof ManifestRefView) {
                ObjectId ref = ((ManifestRefView) obj).getReferenceId();
                if (!getObjectManager().checkObject(ref, !dryRun)) {
                    broken.add(obj);
                }
            }
        }
        return broken;
    }

    /**
     * Add a root {@link Manifest} to traverse and check objects of recursively.
     */
    public ObjectConsistencyCheckOperation addRoot(Manifest.Key key) {
        roots.add(key);
        return this;
    }

    public ObjectConsistencyCheckOperation setDryRun(boolean dry) {
        this.dryRun = dry;
        return this;
    }

}
