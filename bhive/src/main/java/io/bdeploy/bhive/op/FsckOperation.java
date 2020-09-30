package io.bdeploy.bhive.op;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * The {@link FsckOperation} checks the hive for consistency problems.
 * <p>
 * The returned set contains all {@link ElementView}s which are problematic (damaged, missing)
 */
public class FsckOperation extends BHive.Operation<List<ElementView>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private boolean repair;

    @Override
    public List<ElementView> call() throws Exception {
        getObjectManager().invalidateCaches();
        getManifestDatabase().invalidateCaches();

        try (Activity activity = getActivityReporter().start("Checking manifests...", -1)) {
            if (manifests.isEmpty()) {
                SortedSet<Manifest.Key> localManifests = execute(new ManifestListOperation());
                manifests.addAll(localManifests);
            }

            ManifestConsistencyCheckOperation mfCheck = new ManifestConsistencyCheckOperation().setDryRun(!repair);
            ObjectConsistencyCheckOperation objCheck = new ObjectConsistencyCheckOperation().setDryRun(!repair);

            manifests.forEach(k -> {
                mfCheck.addRoot(k);
                objCheck.addRoot(k);
            });

            List<ElementView> problematic = new ArrayList<>();

            // check whether all manifests are still valid, objects might have been removed.
            problematic.addAll(execute(mfCheck));

            // scan and re-hash all objects...
            problematic.addAll(execute(objCheck));

            return problematic;
        } finally {
            getObjectManager().invalidateCaches();
            getManifestDatabase().invalidateCaches();
        }
    }

    /**
     * Add a {@link Manifest} to check.0
     */
    public FsckOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    /**
     * Enabling repair will remove all broken objects from the hive.
     */
    public FsckOperation setRepair(boolean repair) {
        this.repair = repair;
        return this;
    }

}
