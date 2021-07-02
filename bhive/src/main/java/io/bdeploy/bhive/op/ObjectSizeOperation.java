package io.bdeploy.bhive.op;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Measures the disc usage of all {@link ObjectId}s given and returns the sum.
 */
@ReadOnlyOperation
public class ObjectSizeOperation extends BHive.Operation<Long> {

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> objects = new TreeSet<>();

    @Override
    public Long call() throws Exception {
        RuntimeAssert.assertFalse(objects.isEmpty(), "No objects to measure");
        try (Activity activity = getActivityReporter().start("Calculating Object Sizes", -1)) {
            return objects.stream().mapToLong(id -> getObjectManager().db(x -> x.getObjectSize(id))).sum();
        }
    }

    /**
     * Add an object to measure
     */
    public ObjectSizeOperation addObject(Collection<ObjectId> object) {
        this.objects.addAll(object);
        return this;
    }

    /**
     * Add an object to measure
     */
    public ObjectSizeOperation addObject(ObjectId object) {
        objects.add(object);
        return this;
    }

}
