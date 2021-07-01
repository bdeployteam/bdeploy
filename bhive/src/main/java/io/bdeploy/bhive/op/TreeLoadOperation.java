package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.InputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.util.StorageHelper;

/**
 * Loads the specified {@link ObjectId} from its underlying storage in the
 * {@link ObjectDatabase} of the {@link BHive}. The {@link ObjectId} must refer
 * to a {@link Tree}.
 */
@ReadOnlyOperation
public class TreeLoadOperation extends BHive.Operation<Tree> {

    private ObjectId treeId;

    @Override
    public Tree call() throws Exception {
        assertNotNull(treeId, "Tree to load not set");

        try (InputStream is = getObjectManager().db(x -> x.getStream(treeId))) {
            return StorageHelper.fromStream(is, Tree.class);
        }
    }

    /**
     * The {@link Tree}s {@link ObjectId} to load.
     */
    public TreeLoadOperation setTree(ObjectId tree) {
        this.treeId = tree;
        return this;
    }

}
