package io.bdeploy.bhive.op;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.Threads;

/**
 * Locks a directory. The operations waits for an already existing lock to disappear before proceeding (max wait time is
 * {@value DirectoryModificationOperation#RETRIES} times {@value DirectoryModificationOperation#SLEEP_MILLIS}ms ). This
 * means only a single lock can exist (intra- and inter-VM).
 *
 * @see DirectoryAwaitOperation
 */
public class DirectoryLockOperation extends DirectoryModificationOperation<DirectoryLockOperation.LockHandle> {

    private static final Logger log = LoggerFactory.getLogger(DirectoryLockOperation.class);
    private static final ReentrantLock lock = new ReentrantLock(true);

    @FunctionalInterface
    public interface LockHandle {

        void unlock();
    }

    @Override
    public LockHandle doCall(Path lockFile) {
        String content = "";
        if (getLockContentSupplier() != null) {
            content = getLockContentSupplier().get();
        }

        boolean infoWritten = false;
        // make acquisition of the JVM overarching lock fair by queueing threads
        // in this JVM on a reentrant lock. Otherwise, threads in the JVM will
        // be competing for the lock file much more than required.
        lock.lock();
        try {
            for (int i = 0; i < RETRIES; ++i) {
                try {
                    // ATTENTION: it seems that this code will not fully atomically create the file *with* content.
                    // The file is created atomically, but its content is written in a separate operation. Thus there
                    // is potential for a race condition.
                    Files.write(lockFile, Collections.singletonList(content), StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.SYNC).toFile().deleteOnExit();
                    return () -> PathHelper.deleteIfExistsRetry(lockFile);
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
                    if (!Threads.sleep(SLEEP_MILLIS)) {
                        break;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot lock root", e);
                }
            }
            throw new IllegalStateException("Retries exceeded or interrupted while waiting to lock " + lockFile
                    + ". Please check manually if another process is still running and delete the lock file manually.");
        } finally {
            lock.unlock();
        }
    }

    /** Validates whether the given lock file is still valid */
    static boolean isLockFileValid(Path lockFile, Predicate<String> lockContentValidator) {
        // No content validator. Assuming the lock is still valid
        if (lockContentValidator == null) {
            return true;
        }

        // Read the lock file to check if the content is still valid
        try {
            List<String> lines = Files.readAllLines(lockFile);
            // If we have a validator, all empty locks are invalid as well.
            boolean nullOrEmpty = lines.isEmpty() || StringHelper.isNullOrEmpty(lines.get(0));

            if (nullOrEmpty) {
                // an empty lock file is regarded as valid - always! if a validator is set, an empty file
                // is either: 1) a file that was created by a server which did not yet have a validator set,
                // in which case it is OK to require manual deletion (this should never happen), or 2) a file
                // which is currently being written by another thread/vm and not yet complete.
                log.warn("Empty lock file detected, assuming it is valid: {}", lockFile);
                return true;
            }

            if (!lockContentValidator.test(lines.get(0))) {
                log.warn("Stale lock file detected, forcefully resolving ({})...", lines);
                PathHelper.deleteIfExistsRetry(lockFile);
                return false;
            }
            return true;
        } catch (NoSuchFileException | FileNotFoundException fne) {
            // this is "OK", as the file was removed while we're trying to validate it. we can immediately
            // retry to acquire the lock.
            return false;
        } catch (Exception ve) {
            log.warn("Cannot validate lock file, assuming it is valid: {}: {}", lockFile, ve);
            return true;
        }
    }
}
