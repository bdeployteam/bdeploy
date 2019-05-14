package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Insert one or more {@link Manifest}s into the {@link BHive}. No object
 * presence and consistency is checked. This means that the resulting
 * {@link Manifest} might be broken due to missing {@link ObjectId}s in the
 * {@link BHive}.
 * <p>
 * This operation is used internally when transferring {@link Manifest}s and
 * objects from one {@link BHive} to another {@link BHive}.
 */
public class InsertManifestOperation extends BHive.Operation<Long> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedMap<Manifest.Key, Manifest> manifests = new TreeMap<>();

    @Override
    public Long call() throws Exception {
        assertFalse(manifests.isEmpty(), "Nothing to insert");

        try (Activity activity = getActivityReporter().start("Inserting manifests...", -1)) {
            for (Map.Entry<Manifest.Key, Manifest> entry : manifests.entrySet()) {
                if (getManifestDatabase().hasManifest(entry.getValue().getKey())) {
                    continue;
                }
                getManifestDatabase().addManifest(entry.getValue());
                activity.workAndCancelIfRequested(1);
            }
        }

        return Long.valueOf(manifests.size());
    }

    /**
     * @param manifest the {@link Manifest} to insert
     * @return this for chaining.
     */
    public InsertManifestOperation addManifest(Manifest manifest) {
        manifests.put(manifest.getKey(), manifest);
        return this;
    }

}
