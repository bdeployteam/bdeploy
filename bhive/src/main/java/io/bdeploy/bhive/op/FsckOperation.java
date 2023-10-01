package io.bdeploy.bhive.op;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
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
public class FsckOperation extends BHive.Operation<Set<ElementView>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private boolean repair;

    @Override
    public Set<ElementView> call() throws Exception {
        getObjectManager().invalidateCaches();
        getManifestDatabase().invalidateCaches();

        try (Activity activity = getActivityReporter().start("Checking", -1)) {
            if (manifests.isEmpty()) {
                Set<Manifest.Key> localManifests = execute(new ManifestListOperation());
                if (localManifests.isEmpty()) {
                    return Collections.emptySet();
                }

                manifests.addAll(localManifests);
            }

            ManifestConsistencyCheckOperation mfCheck = new ManifestConsistencyCheckOperation().setDryRun(!repair);
            ObjectConsistencyCheckOperation objCheck = new ObjectConsistencyCheckOperation().setDryRun(!repair);

            manifests.forEach(k -> {
                mfCheck.addRoot(k);
                objCheck.addRoot(k);
            });

            Set<ElementView> problematic = new TreeSet<>();

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
     * Add a {@link Manifest} to check.
     */
    public FsckOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    /**
     * Add multiple {@link Manifest} to check.
     */
    public FsckOperation addManifests(Collection<Manifest.Key> keys) {
        manifests.addAll(keys);
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
