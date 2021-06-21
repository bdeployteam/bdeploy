package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.Threads;

/**
 * Waits until a given directory is unlocked.
 *
 * @see LockDirectoryOperation
 * @see ReleaseDirectoryLockOperation
 */
public class AwaitDirectoryLockOperation extends BHive.Operation<Void> {

    private Path directory;

    @Override
    public Void call() throws Exception {
        assertNotNull(directory, "No directory to await.");

        Path lockFile = directory.resolve(LockDirectoryOperation.LOCK_FILE);
        for (int i = 0; i < 100_000; ++i) {
            if (!Files.exists(lockFile) || !LockDirectoryOperation.isLockFileValid(lockFile, getLockContentValidator())) {
                return null;
            }
            if (!Threads.sleep(10)) {
                break;
            }
        }
        throw new IllegalStateException("Retries exceeded or interrupted while waiting that lock " + lockFile
                + " is released. Please check manually if another process is still running and delete the lock file manually.");
    }

    /**
     * Sets the directory that should be awaited.
     */
    public AwaitDirectoryLockOperation setDirectory(Path directory) {
        this.directory = directory;
        return this;
    }

}
