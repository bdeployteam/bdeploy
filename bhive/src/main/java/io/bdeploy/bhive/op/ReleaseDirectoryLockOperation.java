package io.bdeploy.bhive.op;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;

/**
 * Resolves an existing lock by deleting the lock file.
 *
 * @see LockDirectoryOperation
 * @see AwaitDirectoryLockOperation
 */
public class ReleaseDirectoryLockOperation extends DirectoryLockModificationOperation {

    @Override
    public void doCall(Path lockFile) throws Exception {
        PathHelper.deleteRecursiveRetry(lockFile);
    }
}
