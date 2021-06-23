package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;

/**
 * A marker database acts as temporary synchronization and locking over threads and even JVMs.
 * <p>
 * An example are {@link BHiveTransactions}. They use a {@link MarkerDatabase} to "mark" each object written. As long as there is
 * not manifest inserted in the BHive, these objects would be dangling, and subject to removal by prune. The
 * {@link PruneOperation} takes into account any marked object by any transaction and does not touch them.
 */
public class MarkerDatabase extends ObjectDatabase {

    public MarkerDatabase(Path root, ActivityReporter reporter) {
        super(root, root.resolve("tmp"), reporter, null);
    }

    /**
     * Marks the given {@link ObjectId}. The operation is thread-safe, but only if called with different {@link ObjectId}.
     * <p>
     * The caller(s) must make sure to call this method only once with any given {@link ObjectId}.
     */
    public void addMarker(ObjectId id) {
        if (hasObject(id)) {
            return;
        }

        locked(() -> {
            if (hasObject(id)) {
                return;
            }
            Path markerFile = getObjectFile(id);
            PathHelper.mkdirs(markerFile.getParent());
            try {
                Files.createFile(markerFile);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot add marker for " + id, e);
            }
        });
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

}
