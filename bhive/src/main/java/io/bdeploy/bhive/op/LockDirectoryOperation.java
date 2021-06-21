package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.Threads;

/**
 * Lock a directory. The operations waits for an already existing lock to disappear before proceeding (max 1000 seconds, ~16
 * minutes). This means only a single lock can exist (intra- & inter-VM).
 *
 * @see ReleaseDirectoryLockOperation
 * @see AwaitDirectoryLockOperation
 */
public class LockDirectoryOperation extends BHive.Operation<Void> {

    private static final Logger log = LoggerFactory.getLogger(LockDirectoryOperation.class);

    static final String LOCK_FILE = ".lock";

    private Path directory;

    @Override
    public Void call() throws Exception {
        assertNotNull(directory, "No directory to lock.");

        Path lockFile = directory.resolve(LOCK_FILE);

        String content = "";
        if (getLockContentSupplier() != null) {
            content = getLockContentSupplier().get();
        }

        boolean infoWritten = false;
        for (int i = 0; i < 100_000; ++i) {
            try {
                Files.write(lockFile, Collections.singletonList(content), StandardOpenOption.CREATE_NEW).toFile().deleteOnExit();
                return null;
            } catch (IOException e) {
                // validate to find stale lock files
                if (!isLockFileValid(lockFile, getLockContentValidator())) {
                    continue;
                }
                // inform the user that we're about to wait...
                if (!infoWritten) {
                    log.info("Waiting for {}", directory);
                    infoWritten = true;
                }
                // delay a little...
                if (!Threads.sleep(10)) {
                    break;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot lock root", e);
            }
        }
        throw new IllegalStateException("Retries exceeded or interrupted while waiting to lock " + lockFile
                + ". Please check manually if another process is still running and delete the lock file manually.");
    }

    /**
     * Sets the directory that should be locked.
     */
    public LockDirectoryOperation setDirectory(Path directory) {
        this.directory = directory;
        return this;
    }

    /** Validates whether or not the given lock file is still valid */
    static boolean isLockFileValid(Path lockFile, Predicate<String> lockContentValidator) {
        // No content validator. Assuming the lock is still valid
        if (lockContentValidator == null) {
            return true;
        }

        // Read the lock file to check if the content is still valid
        try {
            List<String> lines = Files.readAllLines(lockFile);
            if (!lines.isEmpty() && !StringHelper.isNullOrEmpty(lines.get(0)) && !lockContentValidator.test(lines.get(0))) {
                log.warn("Stale lock file detected, forcefully resolving...");
                Files.delete(lockFile);
                return false;
            }
            return true;
        } catch (NoSuchFileException | FileNotFoundException fne) {
            return false;
        } catch (IOException ve) {
            log.warn("Cannot validate lock file, assuming it is valid: {}: {}", lockFile, ve.toString());
            return true;
        }
    }

}
