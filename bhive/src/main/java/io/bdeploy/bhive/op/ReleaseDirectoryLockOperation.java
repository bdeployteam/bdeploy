package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.PathHelper;

/**
 * Resolves an existing lock by deleting the lock file.
 *
 * @see LockDirectoryOperation
 * @see AwaitDirectoryLockOperation
 */
public class ReleaseDirectoryLockOperation extends BHive.Operation<Void> {

    private Path directory;

    @Override
    public Void call() throws Exception {
        assertNotNull(directory, "No directory to unlock.");

        PathHelper.deleteRecursiveRetry(directory.resolve(LockDirectoryOperation.LOCK_FILE));
        return null;
    }

    /**
     * Sets the directory that should be unlocked.
     */
    public ReleaseDirectoryLockOperation setDirectory(Path directory) {
        this.directory = directory;
        return this;
    }

}
