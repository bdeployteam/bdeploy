package io.bdeploy.bhive.op;

import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.ObjectId;

/**
 * Checks whether the given {@link ObjectId}s exist in the {@link BHive}.
 */
public class ObjectExistsOperation extends BHive.Operation<SortedSet<ObjectId>> {

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> objects = new TreeSet<>();

    @Override
    public SortedSet<ObjectId> call() throws Exception {
        SortedSet<ObjectId> existing = new TreeSet<>();

        for (ObjectId o : objects) {
            if (getObjectManager().db(x -> x.hasObject(o))) {
                existing.add(o);
            }
        }

        return existing;
    }

    /**
     * Add an {@link ObjectId} to check for existence
     */
    public ObjectExistsOperation addObject(ObjectId obj) {
        objects.add(obj);
        return this;
    }

}
