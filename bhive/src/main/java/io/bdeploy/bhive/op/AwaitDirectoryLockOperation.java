package io.bdeploy.bhive.op;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.Threads;

/**
 * Waits until a given directory is unlocked.
 *
 * @see LockDirectoryOperation
 * @see ReleaseDirectoryLockOperation
 */
public class AwaitDirectoryLockOperation extends DirectoryLockModificationOperation {

    @Override
    public void doCall(Path lockFile) throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            if (!PathHelper.exists(lockFile) || !LockDirectoryOperation.isLockFileValid(lockFile, getLockContentValidator())) {
                return;
            }
            if (!Threads.sleep(SLEEP_MILLIS)) {
                break;
            }
        }
        throw new IllegalStateException("Retries exceeded or interrupted while waiting that lock " + lockFile
                + " is released. Please check manually if another process is still running and delete the lock file manually.");
    }
}
