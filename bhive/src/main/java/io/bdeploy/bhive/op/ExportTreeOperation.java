package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ReferenceHandler;

/**
 * Export a {@link Tree} recursively into a directory and return the
 */
@ReadOnlyOperation
public class ExportTreeOperation extends BHive.Operation<Void> {

    private Path target;
    private ObjectId treeId;
    private ReferenceHandler refHandler;

    @Override
    public Void call() throws Exception {
        assertNotNull(target, "Target path not set");
        assertNotNull(treeId, "Source tree not set");

        getObjectManager().exportTree(treeId, target, refHandler);

        return null;
    }

    /**
     * Set the path to export to. The given directory will be recursively filled
     * from the {@link BHive}.
     */
    public ExportTreeOperation setTargetPath(Path target) {
        this.target = target;
        return this;
    }

    public ExportTreeOperation setSourceTree(ObjectId treeId) {
        this.treeId = treeId;
        return this;
    }

    /**
     * Set a custom reference handler which takes care of nested (recursive) manifest references.
     */
    public ExportTreeOperation setReferenceHandler(ReferenceHandler handler) {
        this.refHandler = handler;
        return this;
    }

}
