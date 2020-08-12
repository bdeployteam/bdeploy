package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;
import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;
import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Future;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectManager;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.FutureHelper;

/**
 * Inserts one of more objects by {@link ObjectId} using a given
 * {@link ObjectManager} to read data into the {@link BHive}.
 * <p>
 * This operation is used internally when transferring {@link Manifest}s and
 * objects from one {@link BHive} to another {@link BHive}.
 */
public class InsertExistingObjectsOperation extends BHive.Operation<Long> {

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> objects = new TreeSet<>();
    private ObjectManager sourceMgr;

    @Override
    public Long call() throws Exception {
        assertFalse(objects.isEmpty(), "Nothing to insert");
        assertNotNull(sourceMgr, "No source object manager");

        Activity inserting = getActivityReporter().start("Inserting objects...", objects.size());

        try {
            List<Future<?>> inserts = new ArrayList<>();
            for (ObjectId obj : objects) {
                if (Boolean.TRUE.equals(getObjectManager().db(x -> x.hasObject(obj)))) {
                    // have it already.
                    continue;
                }

                inserts.add(submitFileOperation(() -> {
                    try (InputStream is = sourceMgr.db(x -> x.getStream(obj))) {
                        ObjectId newId = getObjectManager().db(x -> x.addObject(is));
                        assertTrue(newId.equals(obj), "Copy produced different ID - something is broken!");
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot insert object " + obj, e);
                    }
                    inserting.workAndCancelIfRequested(1);
                }));
            }

            FutureHelper.awaitAll(inserts);
        } finally {
            inserting.done();
        }

        return Long.valueOf(objects.size());
    }

    /**
     * Add an {@link ObjectId} to copy from the source {@link ObjectManager}.
     */
    public InsertExistingObjectsOperation addObject(ObjectId object) {
        objects.add(object);
        return this;
    }

    /**
     * Set the source {@link ObjectManager} which provides the actual data for each
     * {@link ObjectId}.
     */
    public InsertExistingObjectsOperation setSourceObjectManager(ObjectManager db) {
        sourceMgr = db;
        return this;
    }

}
