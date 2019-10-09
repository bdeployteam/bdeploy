package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;

public class MarkerDatabase extends ObjectDatabase {

    public MarkerDatabase(Path root, ActivityReporter reporter) {
        super(root, root.resolve("tmp"), reporter);
    }

    public void addMarker(ObjectId id) {
        Path markerFile = getObjectFile(id);
        PathHelper.mkdirs(markerFile.getParent());
        try {
            Files.createFile(markerFile);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot add marker for " + id, e);
        }
    }

    public void removeMarker(ObjectId id) {
        super.removeObject(id);
    }

    @Override
    public InputStream getStream(ObjectId id) throws IOException {
        throw new UnsupportedOperationException("Marker-only Database");
    }

    @Override
    protected ObjectId internalAddObject(ObjectWriter writer) throws IOException {
        throw new UnsupportedOperationException("Marker-only Database");
    }

    @Override
    public void removeObject(ObjectId id) {
        throw new UnsupportedOperationException("Marker-only Database");
    }

    /**
     * Lock a directory. The lock can be awaited using {@link #waitRootLock(Path)}.
     * <p>
     * The method waits for an already existing lock to disappear before proceeding (max 1000 seconds, ~16 minutes). This means
     * only a single lock can exist (intra- & inter-VM).
     *
     * @param root the root directory to lock.
     */
    public static void lockRoot(Path root) {
        for (int i = 0; i < 10_000; ++i) {
            try {
                Files.createFile(root.resolve(".lock"));
                return;
            } catch (FileAlreadyExistsException e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot lock root", e);
            }
        }

        throw new IllegalStateException("Retries exceeded or interrupted, failed to lock marker root");
    }

    /**
     * Wait for a root lock to disappear (max 1000 seconds, ~16 minutes).
     *
     * @param root the root to wait for
     */
    public static void waitRootLock(Path root) {
        Path lockFile = root.resolve(".lock");
        for (int i = 0; i < 10_000; ++i) {
            if (Files.exists(lockFile)) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                return;
            }
        }

        throw new IllegalStateException("Retries exceeded or interrupted, failed to wait for marker root lock");
    }

    /**
     * @param root root to unlock
     */
    public static void unlockRoot(Path root) {
        PathHelper.deleteRecursive(root.resolve(".lock"));
    }

}
