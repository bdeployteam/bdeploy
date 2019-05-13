package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Import a single {@link Path} into the {@link ObjectDatabase}. Useful mainly
 * when building artificial {@link Tree}.
 */
public class ImportFileOperation extends BHive.Operation<ObjectId> {

    private Path file;

    @Override
    public ObjectId call() throws Exception {
        try (Activity activity = getActivityReporter().start("Importing file...", -1)) {
            assertNotNull(file, "File to import not set");
            return getObjectManager().db(x -> x.addObject(file));
        }
    }

    /**
     * Set the {@link Path} to import from. Must be an existing file.
     */
    public ImportFileOperation setFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }
        this.file = file;
        return this;
    }

}
