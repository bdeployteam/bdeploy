/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;

/**
 * A {@link Path} based store for objects. The meaning of {@link Path} may
 * change depending on the underlying {@link FileSystem}, it may - for instance
 * - be a ZIP file or a directory.
 */
public class ObjectDatabase extends LockableDatabase {

    /**
     * The maximum size for any file to be fully loaded into memory. If this limit
     * is exceeded, the {@link ObjectDatabase} is required to stream the file
     * content into a temporary file before adding it to the database.
     */
    static final long MAX_BUFFER_SIZE = 10l * 1024l * 1024l; // 10M

    private final Path root;
    private final Path tmp;
    private final ActivityReporter reporter;

    /**
     * Create a new {@link ObjectDatabase} at the given root. The database is not
     * required to exist yet, it will be created initially empty in this case.
     *
     * @param root the root {@link Path} where the database is located.
     * @param tmp directory to store temporary files into.
     * @param reporter an {@link ActivityReporter} used to report possibly long
     *            running operations.
     */
    public ObjectDatabase(Path root, Path tmp, ActivityReporter reporter) {
        super(root);
        this.root = root;
        this.tmp = tmp;
        this.reporter = reporter;

        if (!Files.exists(root)) {
            PathHelper.mkdirs(root);
        }

        if (!Files.exists(tmp)) {
            PathHelper.mkdirs(tmp);
        }
    }

    /**
     * Retrieves an InputStream from which the actual content of an object with the
     * given {@link ObjectId} can be read.
     *
     * @param id the {@link ObjectId} of the object to lookup.
     * @return an {@link InputStream} to the object.
     * @throws IOException in case of an error.
     */
    public InputStream getStream(ObjectId id) throws IOException {
        if (!hasObject(id)) {
            throw new IllegalStateException("Missing object: " + id);
        }
        return Files.newInputStream(getObjectFile(id));
    }

    /**
     * Checks whether the object with the given {@link ObjectId} exists in the
     * database.
     *
     * @param id the {@link ObjectId} to check
     * @return <code>true</code> if it exists, <code>false</code> otherwise.
     */
    public boolean hasObject(ObjectId id) {
        // java.io.File.exists() seems to be about 30% faster than java.nio.file.Files.exists() on Windows
        // e.g. 60 microseconds vs. 85 microseconds -- this calculates to 25 seconds for 1,000,000 calls...
        Path path = getObjectFile(id);
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return getObjectFile(id).toFile().exists();
        } else {
            return Files.exists(getObjectFile(id));
        }
    }

    /**
     * Add a new object to the database from an existing file. This method will
     * delegate to {@link #addObject(byte[])} or {@link #addObject(InputStream)}
     * depending on the size of the file to add.
     *
     * @param file {@link Path} to the file to add
     * @return the calculated {@link ObjectId} under which the object has been
     *         persisted.
     * @throws IOException in case of an error.
     */
    public ObjectId addObject(Path file) throws IOException {
        long size = Files.size(file);
        if (size >= MAX_BUFFER_SIZE) {
            // need to stream
            try (InputStream is = Files.newInputStream(file)) {
                return addObject(is);
            }
        } else {
            // can read fully in memory buffer
            // TODO: seems to fail sometimes for large files on JDK11 (in Eclipse only?).
            byte[] bytes = Files.readAllBytes(file);
            return addObject(bytes);
        }
    }

    /**
     * Add a new object to the database from in-memory data.
     *
     * @param bytes the objects content
     * @return the calculated {@link ObjectId} under which the object has been
     *         persisted.
     * @throws IOException in case of an error.
     */
    public ObjectId addObject(byte[] bytes) throws IOException {
        return internalAddObject(p -> {
            Files.write(p, bytes);
            return ObjectId.create(bytes, 0, bytes.length);
        });

    }

    /**
     * Add a new object to the database from the given {@link InputStream}. The
     * {@link ObjectId} is calculated while writing the file contents to a temporary
     * file, which is then moved to the final location.
     * <p>
     * Attention: this can have a huge performance impact when operating on a
     * {@link FileSystem} which does not support moving.
     *
     * @param stream the raw object's data
     * @return the calculated {@link ObjectId} under which the object has been
     *         persisted.
     * @throws IOException in case of an error.
     */
    public ObjectId addObject(InputStream stream) throws IOException {
        return internalAddObject(p -> ObjectId.createByCopy(stream, p));
    }

    protected ObjectId internalAddObject(ObjectWriter writer) throws IOException {
        Path tmpFile = Files.createTempFile(this.tmp, "obj", ".tmp");
        try {
            ObjectId id = writer.write(tmpFile);
            Path target = getObjectFile(id);

            locked(() -> {
                if (hasObject(id)) {
                    return;
                }
                PathHelper.mkdirs(target.getParent());
                Files.move(tmpFile, target);
            });
            return id;
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /**
     * Verifies that a given {@link ObjectId}s backing file still hashes to the
     * given {@link ObjectId}. This can be used to detect corruption of objects.
     */
    public boolean checkObject(ObjectId id) throws IOException {
        try (InputStream is = getStream(id)) {
            ObjectId newId = ObjectId.createFromStreamNoCopy(is);
            return newId.equals(id);
        }
    }

    /**
     * Removes the given {@link ObjectId}s backing file from the database.
     * <p>
     * WARNING: use with care, this can cause corruption!
     */
    public void removeObject(ObjectId id) {
        if (!hasObject(id)) {
            return;
        }

        Path file = getObjectFile(id);
        locked(() -> Files.delete(file));
    }

    /**
     * Calculate the {@link Path} where a certain {@link ObjectId} can be found in
     * the database. Use with caution.
     */
    public Path getObjectFile(ObjectId id) {
        String rawId = id.getId();
        String l1 = rawId.substring(0, 2);
        String l2 = rawId.substring(2, 4);
        return root.resolve(root.getFileSystem().getPath(l1, l2, rawId));
    }

    /**
     * Retrieve the file size for the file backing {@link ObjectId}.
     */
    public long getObjectSize(ObjectId id) throws IOException {
        return Files.size(getObjectFile(id));
    }

    /**
     * Scan for and retrieve all objects in the database. This is potentially an
     * expensive operation, as object presence is not cached.
     *
     * @throws IOException in case of an error.
     */
    public SortedSet<ObjectId> getAllObjects() throws IOException {
        Activity scan = reporter.start("Scanning objects...", 0);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(Path::getFileName).map(Object::toString).map(ObjectId::parse)
                    .filter(Objects::nonNull).peek(e -> scan.workAndCancelIfRequested(1))
                    .collect(Collectors.toCollection(TreeSet::new));
        } finally {
            scan.done();
        }
    }

}
