package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;

public class MarkerDatabase extends ObjectDatabase {

    private static final byte[] DEFAULT_MARKER = new byte[] { 0x1 };

    public MarkerDatabase(Path root, ActivityReporter reporter) {
        super(root, root.resolve("tmp"), reporter);
    }

    public void addMarker(ObjectId id) {
        Path markerFile = getObjectFile(id);
        PathHelper.mkdirs(markerFile.getParent());
        try {
            Files.write(markerFile, DEFAULT_MARKER);
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

}
