package io.bdeploy.bhive.op;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ObjectExistsOperation.Result;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Checks whether the given {@link ObjectId}s exist in the {@link BHive}.
 */
@ReadOnlyOperation
public class ObjectExistsOperation extends BHive.Operation<Result> {

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final Set<ObjectId> objects = new LinkedHashSet<>();

    @Override
    public Result call() throws Exception {
        Result result = new Result();
        try (Activity activity = getActivityReporter().start("Looking up Objects", objects.size())) {
            for (ObjectId o : objects) {
                if (Boolean.TRUE.equals(getObjectManager().db(x -> x.hasObject(o)))) {
                    result.existing.add(o);
                } else {
                    result.missing.add(o);
                }
                activity.worked(1);
            }
        }
        return result;
    }

    /**
     * Add an {@link ObjectId} to check for existence
     */
    public ObjectExistsOperation addObject(ObjectId obj) {
        objects.add(obj);
        return this;
    }

    /**
     * Add all {@link ObjectId} to check for existence
     */
    public ObjectExistsOperation addAll(Collection<ObjectId> objs) {
        objects.addAll(objs);
        return this;
    }

    /**
     * Result object indicating what is missing and existing.
     */
    public static class Result {

        /**
         * Objects that are existing
         */
        public final Set<ObjectId> existing = new LinkedHashSet<>();

        /**
         * Objects that are missing
         */
        public final Set<ObjectId> missing = new LinkedHashSet<>();

        /**
         * Returns whether the given objects is existing.
         */
        public boolean isExisting(ObjectId obj) {
            return existing.contains(obj);
        }

        /**
         * Returns whether the given objects is missing.
         */
        public boolean isMissing(ObjectId obj) {
            return missing.contains(obj);
        }

    }

}
