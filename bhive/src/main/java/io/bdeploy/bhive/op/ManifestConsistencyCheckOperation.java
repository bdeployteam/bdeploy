package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;

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
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Operation to check manifest consistency. A {@link Manifest} is considered
 * consistent if all required {@link ObjectId}s are present in the {@link BHive}
 * recursively.
 * <p>
 * Returns the list of missing/damaged {@link ElementView}s, which identify the underlying elements.
 * <p>
 * This is a quick check for fundamental consistency, which is complemented by {@link ObjectConsistencyCheckOperation} - which
 * checks each <b>existing</b> and <b>reachable</b> object for actual data integrity.
 */
public class ManifestConsistencyCheckOperation extends BHive.Operation<Set<ElementView>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private boolean dryRun = true;

    @Override
    public Set<ElementView> call() {
        assertFalse(manifests.isEmpty(), "Nothing to check");

        Set<ElementView> dmg = new TreeSet<>();
        try (Activity activity = getActivityReporter().start("Checking Manifests", manifests.size())) {
            for (Manifest.Key key : manifests) {
                List<ElementView> broken = new ArrayList<>();

                // it is OK if it no longer exists.
                if (!Boolean.TRUE.equals(execute(new ManifestExistsOperation().setManifest(key)))) {
                    continue;
                }

                TreeView state = execute(new ScanOperation().setManifest(key));
                state.visit(new TreeVisitor.Builder().onMissing(broken::add).build());

                if (!broken.isEmpty() && !dryRun) {
                    execute(new ManifestDeleteOperation().setToDelete(key));
                }

                dmg.addAll(broken);
                activity.workAndCancelIfRequested(1);
            }
        }
        return dmg;
    }

    /**
     * Add a {@link Manifest} to check for consistency.
     */
    public ManifestConsistencyCheckOperation addRoot(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public ManifestConsistencyCheckOperation setDryRun(boolean dry) {
        this.dryRun = dry;
        return this;
    }

}
