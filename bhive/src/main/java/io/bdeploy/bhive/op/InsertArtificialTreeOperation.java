package io.bdeploy.bhive.op;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Create a {@link Tree} in the {@link ObjectDatabase} and return its
 * {@link ObjectId}.
 */
public class InsertArtificialTreeOperation extends BHive.TransactedOperation<ObjectId> {

    private Tree.Builder builder;

    @Override
    public ObjectId callTransacted() throws Exception {
        try (Activity activity = getActivityReporter().start("Inserting tree...", -1)) {
            return getObjectManager().insertTree(builder.build());
        }
    }

    /**
     * Set the {@link Tree} to insert.
     */
    public InsertArtificialTreeOperation setTree(Tree.Builder builder) {
        this.builder = builder;
        return this;
    }

}
