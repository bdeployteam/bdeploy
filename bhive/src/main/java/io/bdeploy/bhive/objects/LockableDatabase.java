package io.bdeploy.bhive.objects;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

/**
 * Base class for a database which requires locked modifications for
 * parallel-safety (JVM overarching).
 */
abstract public class LockableDatabase {

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

            // super protected :)
            try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                    FileChannel channel = raf.getChannel();
                    FileLock lock = channel.lock()) {
                toLock.run();
            }
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
