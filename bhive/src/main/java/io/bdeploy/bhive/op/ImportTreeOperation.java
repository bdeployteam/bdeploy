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
public class ImportTreeOperation extends BHive.TransactedOperation<ObjectId> {

    private Path toImport;
    private boolean skipEmpty = false;

    @Override
    public ObjectId callTransacted() throws Exception {
        assertNotNull(toImport, "Source path not set");

        try (Activity activity = getActivityReporter().start("Importing tree...", -1)) {
            return getObjectManager().importTree(toImport, skipEmpty);
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

    /**
     * @param skip whether to skip empty directories while importing
     */
    public ImportTreeOperation setSkipEmpty(boolean skip) {
        this.skipEmpty = skip;
        return this;
    }

}
