package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;

import java.io.InputStream;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Resolve {@link Manifest}s referenced in a {@link Tree} by the given
 * {@link ObjectId}s.
 */
@ReadOnlyOperation
public class ManifestRefLoadOperation extends BHive.Operation<SortedMap<ObjectId, Manifest.Key>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<ObjectId> ids = new TreeSet<>();

    @Override
    public SortedMap<ObjectId, Manifest.Key> call() throws Exception {
        assertFalse(ids.isEmpty(), "Nothing to load");

        try (Activity activity = getActivityReporter().start("Loading Relations", -1)) {
            SortedMap<ObjectId, Key> refs = new TreeMap<>();
            for (ObjectId id : ids) {
                try (InputStream is = getObjectManager().db(x -> x.getStream(id))) {
                    refs.put(id, StorageHelper.fromStream(is, Manifest.Key.class));
                }

            }
            return refs;
        }
    }

    public ManifestRefLoadOperation addManifestRef(ObjectId manifest) {
        ids.add(manifest);
        return this;
    }

}
