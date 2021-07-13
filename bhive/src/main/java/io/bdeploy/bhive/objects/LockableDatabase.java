package io.bdeploy.bhive.objects;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

/**
 * Base class for a database which requires locked modifications for
 * parallel-safety (JVM overarching).
 */
public abstract class LockableDatabase {

    private final File lockFile;

    /**
     * @param root the root directory of the database. If the underlying filesystem
     *            does not support {@link Path#toFile()} (for instance ZIP file),
     *            there is no locking capability.
     */
    public LockableDatabase(Path root) {
        this.lockFile = determineLockFile(root);
    }

    private static File determineLockFile(Path root) {
        try {
            return root.resolve(".dblock").toFile();
        } catch (UnsupportedOperationException e) {
            // in case of zip file, ... toFile not supported, no locking.
            // the assumption is that not multiple VMs access the same ZIP file concurrently.
            return null;
        }
    }

    /**
     * @param toLock a database-modifying operation (insertion, deletion, ...).
     */
    protected synchronized void locked(LockedOperation toLock) {
        try {
            // happens for ZIP files and others (?) which don't support Path.toFile().
            if (lockFile == null) {
                toLock.run();
                return;
            }

            do {
                long xctpCount = 0;

                try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                        FileChannel channel = raf.getChannel();
                        FileLock lock = channel.lock()) {
                    toLock.run();
                    return;
                } catch (IOException ioe) {
                    // this one is tricky. fcntl on linux will hold locks per-process (not thread), and has a deadlock detection.
                    // if another (multi-threaded) process and we (multi-threaded) lock files, fcntl may detect a process level
                    // deadlock, even though the locks are fine on a thread level. There is no easy way to work around this here,
                    // especially not if we do not want to dramatically increase lock contention in the whole process. This means
                    // we go for a quick'n'dirty approach and simply retry in this case.
                    if (ioe.getMessage().equals("Resource deadlock avoided") && xctpCount++ <= 10) {
                        wait(5);
                        continue;
                    }
                    throw ioe;
                }
            } while (true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("locked execution interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("locked execution failed", e);
        }
    }

    /**
     * Interface for operations that need to lock the database (basically every
     * writing operation).
     */
    @FunctionalInterface
    protected static interface LockedOperation {

        /**
         * Perform the modification.
         *
         * @throws Exception in case of a problem.
         */
        public void run() throws Exception;
    }

}
