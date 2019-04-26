package io.bdeploy.bhive.op;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Checks for missing and corrupt objects. Missing objects will lead to an
 * exception, as they are required for full tree traversal. Corrupted objects
 * will be collected, and returned.
 */
public class ObjectConsistencyCheckOperation extends BHive.Operation<List<ElementView>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> roots = new TreeSet<>();
    private boolean dryRun = true;

    @Override
    public List<ElementView> call() throws Exception {
        if (roots.isEmpty()) {
            SortedSet<Manifest.Key> localManifests = execute(new ManifestListOperation());
            roots.addAll(localManifests);
        }

        Activity scanning = getActivityReporter().start("Scanning manifest trees...", roots.size());
        List<ElementView> existingElements = new ArrayList<>();
        try {
            for (Manifest.Key key : roots) {
                TreeView state = execute(new ScanOperation().setManifest(key));

                state.visit(new TreeVisitor.Builder().onBlob(existingElements::add).onTree(existingElements::add)
                        .onManifestRef(existingElements::add).build());

                scanning.workAndCancelIfRequested(1);
            }
        } finally {
            scanning.done();
        }

        Activity checking = getActivityReporter().start("Checking objects...", existingElements.size());
        try {
            List<ElementView> broken = new ArrayList<>();
            for (ElementView obj : existingElements) {
                if (!getObjectManager().checkObject(obj.getElementId(), !dryRun)) {
                    broken.add(obj);
                }
                if (obj instanceof ManifestRefView) {
                    ObjectId ref = ((ManifestRefView) obj).getReferenceId();
                    if (!getObjectManager().checkObject(ref, !dryRun)) {
                        broken.add(obj);
                    }
                }
                checking.workAndCancelIfRequested(1);
            }
            return broken;
        } finally {
            checking.done();
        }
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
