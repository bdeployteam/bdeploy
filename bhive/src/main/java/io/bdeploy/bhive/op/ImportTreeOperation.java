package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Import a {@link Path} recursively into the local hive and return the
 * {@link Tree} {@link ObjectId}.
 */
public class ImportTreeOperation extends BHive.Operation<ObjectId> {

    private Path toImport;

    @Override
    public ObjectId call() throws Exception {
        assertNotNull(toImport, "Source path not set");

        try (Activity activity = getActivityReporter().start("Importing tree...", -1)) {
            return getObjectManager().importTree(toImport);
        }
    }

    /**
     * Set the path to import from. The given directory will be recursively imported
     * into the {@link BHive}.
     */
    public ImportTreeOperation setSourcePath(Path toImport) {
        this.toImport = toImport;
        return this;
    }

}
