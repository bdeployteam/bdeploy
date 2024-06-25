package io.bdeploy.bhive.op;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;

/**
 * Resolves an existing lock by deleting the lock file.
 *
 * @see DirectoryLockOperation
 * @see DirectoryAwaitOperation
 */
public class DirectoryReleaseOperation extends DirectoryModificationOperation {

    @Override
    public void doCall(Path lockFile) {
        PathHelper.deleteRecursiveRetry(lockFile);
    }
}
