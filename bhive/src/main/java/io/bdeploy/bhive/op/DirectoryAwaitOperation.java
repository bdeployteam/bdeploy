package io.bdeploy.bhive.op;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.Threads;

/**
 * Waits until a given directory is unlocked.
 *
 * @see DirectoryLockOperation
 * @see DirectoryReleaseOperation
 */
public class DirectoryAwaitOperation extends DirectoryModificationOperation<Void> {

    @Override
    public Void doCall(Path lockFile) {
        for (int i = 0; i < RETRIES; ++i) {
            if (!PathHelper.exists(lockFile) || !DirectoryLockOperation.isLockFileValid(lockFile, getLockContentValidator())) {
                return null;
            }
            if (!Threads.sleep(SLEEP_MILLIS)) {
                break;
            }
        }
        throw new IllegalStateException("Retries exceeded or interrupted while waiting that lock " + lockFile
                + " is released. Please check manually if another process is still running and delete the lock file manually.");
    }
}
