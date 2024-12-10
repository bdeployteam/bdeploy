/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.Threads;

/**
 * A simple key-value data store. For each object that is added to the database a {@linkplain ObjectId ObjectId} is calculated
 * that can be used later to retrieve the content again. The identifier is based on the content. Two objects having the same
 * identifier will be stored once. Additional metadata like the name or type of the added object is not stored.
 * <p>
 * Each object is stored internally as single file named with the {@linkplain ObjectId object identifier}. Files are placed in
 * sub-directories to keep the overall amount of files per directory small. The first four characters of the identifier are used
 * to determine the target directory. Two levels of directories are used. The first level is based on the first two characters and
 * the second level on the next two characters.
 * </p>
 */
public class ObjectDatabase extends LockableDatabase {

    private static final Logger log = LoggerFactory.getLogger(ObjectDatabase.class);

    /**
     * The maximum size for any file to be fully loaded into memory. If this limit
     * is exceeded, the {@link ObjectDatabase} is required to stream the file
     * content into a temporary file before adding it to the database.
     */
    static final long MAX_BUFFER_SIZE = 10L * 1024 * 1024; // 10M

    private final Path root;
    private final Path tmp;
    private final ActivityReporter reporter;
    private final BHiveTransactions transactions;

    /**
     * Create a new {@link ObjectDatabase} at the given root. The database is not
     * required to exist yet, it will be created initially empty in this case.
     *
     * @param root the root {@link Path} where the database is located.
     * @param tmp directory to store temporary files into.
     * @param reporter an {@link ActivityReporter} used to report possibly long
     *            running operations.
     */
    public ObjectDatabase(Path root, Path tmp, ActivityReporter reporter, BHiveTransactions transactions) {
        super(root);
        this.root = root;
        this.tmp = tmp;
        this.reporter = reporter;
        this.transactions = transactions;

        if (!PathHelper.exists(root)) {
            PathHelper.mkdirs(root);
        }

        if (tmp != null && !PathHelper.exists(tmp)) {
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
        return PathHelper.exists(getObjectFile(id));
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
            // due to the heavy performance impact we do NOT sync the output
            // here. We can later on detect problems easily as long as the meta-data
            // is written sync (manifests, etc.).
            Files.write(p, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
            Path target = getObjectFileLocal(id);

            // Done outside the lock and before the existence check. This is to make sure
            // that we touch an object even though another transaction might have inserted
            // it already. If the other transaction would fail, we would still protect the
            // object by marking it for this transaction as well.
            if (transactions != null) {
                transactions.touchObject(id);
            }

            locked(() -> {
                if (hasObject(id)) {
                    return;
                }

                PathHelper.mkdirs(target.getParent());
                PathHelper.moveRetry(tmpFile, target);
            });
            return id;
        } finally {
            try {
                PathHelper.deleteIfExistsRetry(tmpFile);
            } catch (Exception e) {
                log.warn("Cannot clean temporary file while adding object", e);
            }
        }
    }

    /**
     * Verifies that a given {@link ObjectId}s backing file still hashes to the
     * given {@link ObjectId}. This can be used to detect corruption of objects.
     */
    public boolean checkObject(ObjectId id) {
        try (InputStream is = getStream(id)) {
            ObjectId newId = ObjectId.createFromStreamNoCopy(is);
            return newId.equals(id);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot check object {}", id, e);
            }
            return false; // no longer valid - potentially removed by prune, etc.
        }
    }

    /**
     * Removes the given {@link ObjectId}s backing file from the database.
     * <p>
     * WARNING: use with care, this can cause corruption!
     */
    public void removeObject(ObjectId id) {
        Path file = getObjectFileLocal(id);
        if (!PathHelper.exists(file)) {
            return; // not there at all.
        }
        locked(() -> PathHelper.deleteIfExistsRetry(file));
    }

    /**
     * Calculate the {@link Path} where a certain {@link ObjectId} can be found in
     * the database. Use with caution. This can be overridden by implementations to enable pooling.
     */
    public Path getObjectFile(ObjectId id) {
        return getObjectFileLocal(id);
    }

    /**
     * Calculate the {@link Path} where a certain {@link ObjectId} can be found in
     * the database. Use with caution.
     */
    protected final Path getObjectFileLocal(ObjectId id) {
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
     * <p>
     * The consumer will be notified for each {@link ObjectId} which is <b>directly</b>
     * present in this {@link ObjectDatabase}. {@link AugmentedObjectDatabase} will not
     * contribute augmented objects.
     */
    public void walkAllObjects(Consumer<ObjectId> consumer) {
        try (Activity scan = reporter.start("Listing Objects", 0)) {
            long xctpCount = 0;
            do {
                try {
                    try (Stream<Path> walk = Files.walk(root)) {
                        walk.filter(Files::isRegularFile)//
                                .map(Path::getFileName)//
                                .map(Object::toString)//
                                .map(ObjectId::parse).filter(Objects::nonNull)//
                                .forEach(e -> {
                                    scan.workAndCancelIfRequested(1);
                                    consumer.accept(e);
                                });

                        // done. explicit return to escape the exception handling retry loop :)
                        return;
                    } catch (UncheckedIOException e) {
                        // something was removed in the middle of the walk... retry.
                        if (!(e.getCause() instanceof NoSuchFileException) || xctpCount++ > 20) {
                            throw e;
                        }
                    }
                } catch (NoSuchFileException e) {
                    // this happens if the path does not exist at all anymore, so there are zero objects.
                    if (log.isDebugEnabled()) {
                        log.debug("Path to walk not found", e);
                    }
                    return;
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot walk objects", e);
                }

                // Delay the loop a little
                Threads.sleep(50);
            } while (true);
        }
    }
}
