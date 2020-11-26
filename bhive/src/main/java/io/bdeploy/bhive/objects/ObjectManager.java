/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;
import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoInputStreamWrapper;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.MissingObjectView;
import io.bdeploy.bhive.objects.view.SkippedElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.PathHelper;

/**
 * Provides higher level operations on the {@link ObjectDatabase}.
 */
public class ObjectManager {

    private static final Logger log = LoggerFactory.getLogger(ObjectManager.class);

    private final ObjectDatabase db;
    private final ManifestDatabase mdb;
    private final ActivityReporter reporter;
    private final ExecutorService fileOps;

    /**
     * A cache for Tree and ManifestRef objects which need to actually be loaded from disk for correct tree traversal.
     * <p>
     * Assuming a max object size of ~1K, this cache would grow to ~10MB. The average object size is assumed to be less,
     * but the calculation is defensive.
     * <p>
     * For instance a TREE object contains approx. 70 bytes per entry. This means that a 1K tree can hold ~15 entries.
     * The average for a representative large-scale sample application is ~7.
     */
    private final Cache<ObjectId, Object> objectCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    /**
     * Creates a new {@link ObjectManager}. The manager itself has no state. It only
     * provides operations on the underlying {@link ObjectDatabase}
     *
     * @param db the underlying {@link ObjectDatabase}.
     * @param mdb the underlying {@link ManifestDatabase}, only used for
     *            manifest reference lookup.
     * @param reporter used to report long running operations
     * @param fileOps used to parallelize file operations.
     */
    public ObjectManager(ObjectDatabase db, ManifestDatabase mdb, ActivityReporter reporter, ExecutorService fileOps) {
        this.db = db;
        this.mdb = mdb;
        this.reporter = reporter;
        this.fileOps = fileOps;
    }

    /**
     * Import a {@link Path} recursively into the underlying {@link ObjectDatabase}.
     *
     * @param location the location to import recursively
     * @param skipEmpty whether to skip empty directories
     * @return the {@link ObjectId} of the resulting {@link Tree}.
     */
    public ObjectId importTree(Path location, boolean skipEmpty) {
        Activity importing = reporter.start("Importing objects...", 0);
        try {
            return internalImportTree(location, importing, skipEmpty);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot import " + location, e);
        } finally {
            importing.done();
        }
    }

    /**
     * Recursively import a tree, parallelizing imports on the same path levels.
     *
     * @param skipEmpty
     */
    private ObjectId internalImportTree(Path location, Activity importing, boolean skipEmpty) throws IOException {
        Tree.Builder tree = new Tree.Builder();

        List<Future<?>> filesOnLevel = new ArrayList<>();
        try (DirectoryStream<Path> list = Files.newDirectoryStream(location)) {
            for (Path path : list) {
                if (Files.isDirectory(path)) {
                    try {
                        if (skipEmpty && PathHelper.isDirEmpty(path)) {
                            continue;
                        }
                    } catch (UnsupportedOperationException e) {
                        log.warn("Cannot check if directory is empty: {}", path);
                    }

                    // recursively calculate ObjectId from sub-tree.
                    tree.add(new Tree.Key(path.getFileName().toString(), Tree.EntryType.TREE),
                            internalImportTree(path, importing, skipEmpty));
                } else {
                    // insert an actual file into the tree.
                    filesOnLevel.add(fileOps.submit(() -> {
                        try {
                            // store in tree after importing.
                            tree.add(new Tree.Key(path.getFileName().toString(), Tree.EntryType.BLOB), db.addObject(path));
                        } catch (IOException e) {
                            throw new IllegalStateException("cannot insert object from: " + path, e);
                        }
                        importing.workAndCancelIfRequested(1);
                    }));
                }
            }
        }

        // wait for all files on this level.
        FutureHelper.awaitAll(filesOnLevel);

        // insert the tree into the db and return its ObjectId.
        importing.workAndCancelIfRequested(1);
        return insertTree(tree.build());
    }

    /**
     * Exports a given tree (by {@link ObjectId}) to the given location, which must
     * not exist yet.
     *
     * @param tree the {@link ObjectId} of the {@link Tree} to write
     * @param location the target {@link Path} to create
     * @param handler a custom reference handler which takes care of references. If not set, the default will be used (inline
     *            export of manifest reference in place).
     */
    public void exportTree(ObjectId tree, Path location, ReferenceHandler handler) {
        if (handler == null) {
            handler = new DefaultReferenceHandler(this);
        }

        try {
            if (Files.exists(location)) {
                try (Stream<Path> list = Files.list(location)) {
                    if (list.findAny().isPresent()) {
                        throw new IllegalStateException("Location must not exist or be empty: " + location);
                    }
                }
            }

            Path tempLocation = location.getParent().resolve(location.getFileName().toString() + ".xtmp");

            // unfortunately there is no better way to detect a 'ZipPath' as the class is not accessible directly.
            if (tempLocation.getClass().getSimpleName().contains("Zip")) {
                // for ZIPs, don't use the temp location.
                tempLocation = location;
            }

            if (Files.exists(tempLocation)) {
                PathHelper.deleteRecursive(tempLocation);
            }

            Activity exporting = reporter.start("Writing objects...", 0);
            try {
                internalExportTree(tree, tempLocation, tree, tempLocation, exporting, handler);
                Files.move(tempLocation, location, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable t) {
                try {
                    if (Files.exists(tempLocation)) {
                        PathHelper.deleteRecursive(tempLocation);
                    }
                } catch (Throwable it) {
                    t.addSuppressed(it);
                }
                throw t;
            } finally {
                exporting.done();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot export to " + location, e);
        }
    }

    /**
     * Recursively export tree to target location.
     */
    private void internalExportTree(ObjectId tree, Path topLevel, ObjectId topLevelTree, Path location, Activity exporting,
            ReferenceHandler handler) throws IOException {
        PathHelper.mkdirs(location);

        Tree t;
        try {
            t = loadObject(tree, is -> StorageHelper.fromStream(is, Tree.class));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load tree for path " + location);
        }

        List<Future<?>> filesOnLevel = new ArrayList<>();
        for (Map.Entry<Tree.Key, ObjectId> entry : t.getChildren().entrySet()) {
            ObjectId obj = entry.getValue();
            Tree.Key key = entry.getKey();

            Path child = location.resolve(key.getName());
            switch (key.getType()) {
                case BLOB:
                    filesOnLevel.add(fileOps.submit(() -> {
                        try {
                            internalExportBlobByCopy(obj, child);
                        } catch (Exception e) {
                            throw new IllegalStateException("Cannot export BLOB to " + child, e);
                        } finally {
                            exporting.workAndCancelIfRequested(1);
                        }
                    }));
                    break;
                case MANIFEST:
                    handler.onReference(location, key, lookupManifestRef(obj));
                    break;
                case TREE:
                    internalExportTree(obj, topLevel, topLevelTree, child, exporting, handler);
                    break;
                default:
                    break;

            }
        }

        // wait for all files before going up one level.
        FutureHelper.awaitAll(filesOnLevel);

        exporting.workAndCancelIfRequested(1);
    }

    private void internalExportBlobByCopy(ObjectId obj, Path child) {
        // fallback only: create copy of file. determine content type as we go.
        try (ContentInfoInputStreamWrapper is = new ContentInfoInputStreamWrapper(db.getStream(obj),
                PathHelper.getContentInfoUtil())) {
            ObjectId finalId = ObjectId.createByCopy(is, child);
            if (!finalId.equals(obj)) {
                // not good - object in DB seems corrupt.
                throw new IOException("BLOB corruption: " + obj + " (is " + finalId + "), run FSCK");
            }
            setExecutable(child, is.findMatch());
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot export " + obj + " to " + child, ioe);
        }
    }

    /**
     * Sets attributes to make a file executable if required.
     *
     * @param child {@link Path} to the file to check
     * @param hint a potential pre-calculated {@link ContentInfo}
     */
    private void setExecutable(Path child, ContentInfo hint) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(child, PosixFileAttributeView.class);
        if (view != null) {
            hint = PathHelper.getContentInfo(child, hint);
            if (hint == null) {
                return;
            }

            if (PathHelper.isExecutable(hint)) {
                Set<PosixFilePermission> perms = view.readAttributes().permissions();
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                view.setPermissions(perms);
            }
        }
    }

    /**
     * Create a traversable snapshot of the given {@link Tree} state up to a given maximum depth.
     * <p>
     * In case the root tree is damaged, it is wrapped in a dummy {@link TreeView} with a <code>null</code> {@link ObjectId}.
     *
     * @param tree the root tree to scan
     * @param maxDepth maximum scan depth. A depth of 1 will include only direct children at the root level, and so on.
     */
    public TreeView scan(ObjectId tree, int maxDepth, boolean followReferences) {
        ElementView ev;
        if (db.hasObject(tree)) {
            ev = scan(tree, EntryType.TREE, new ArrayDeque<>(), maxDepth, followReferences);
        } else {
            ev = new DamagedObjectView(tree, EntryType.TREE, Collections.singletonList("/"));
        }

        if (ev instanceof TreeView) {
            return (TreeView) ev;
        }

        // wrap in dummy tree to allow type assumption. this happens if the root tree is already missing.
        TreeView tv = new TreeView(null, Collections.emptyList());
        tv.addChild(ev);

        return tv;
    }

    private ElementView scan(ObjectId object, EntryType type, Deque<String> path, int maxDepth, boolean followReferences) {
        // include blobs anyway, only skip following trees
        if (type != EntryType.BLOB && path.size() >= maxDepth) {
            return new SkippedElementView(object, path);
        }
        if (!db.hasObject(object)) {
            return new MissingObjectView(object, type, path);
        }
        switch (type) {
            case BLOB:
                return new BlobView(object, path);
            case MANIFEST:
                Manifest mf = lookupManifestRef(object);
                if (mf == null) {
                    return new MissingObjectView(object, type, path);
                }
                ManifestRefView mrs = new ManifestRefView(object, mf.getKey(), mf.getRoot(), path);

                if (!followReferences) {
                    mrs.addChild(new SkippedElementView(mf.getRoot(), path));
                    return mrs;
                }

                if (!db.hasObject(mf.getRoot())) {
                    mrs.addChild(new MissingObjectView(mf.getRoot(), EntryType.TREE, path));
                    return mrs;
                }

                try {
                    Tree mrt = loadObject(mf.getRoot(), is -> StorageHelper.fromStream(is, Tree.class));
                    scanChildren(mrs, mrt, path, maxDepth, followReferences);
                } catch (Exception e) {
                    mrs.addChild(new DamagedObjectView(mf.getRoot(), type, path));
                }
                return mrs;
            case TREE:
                try {
                    Tree t = loadObject(object, is -> StorageHelper.fromStream(is, Tree.class));
                    TreeView ts = new TreeView(object, path);
                    scanChildren(ts, t, path, maxDepth, followReferences);
                    return ts;
                } catch (Exception e) {
                    return new DamagedObjectView(object, EntryType.TREE, path);
                }
            default:
                throw new IllegalStateException("Unsupported object type: " + type);
        }
    }

    private void scanChildren(TreeView container, Tree tree, Deque<String> path, int maxDepth, boolean followReferences) {
        for (Entry<Key, ObjectId> entry : tree.getChildren().entrySet()) {
            path.addLast(entry.getKey().getName());
            container.addChild(scan(entry.getValue(), entry.getKey().getType(), path, maxDepth, followReferences));
            path.removeLast();
        }
    }

    /**
     * @param tree the root tree to resolve from
     * @param path the path in the tree to resolve
     * @return an {@link InputStream} to the file denoted by the path.
     * @throws IOException
     */
    public InputStream getStreamForRelativePath(ObjectId tree, String... path) throws IOException {
        Tree t = loadObject(tree, is -> StorageHelper.fromStream(is, Tree.class));

        if (path.length > 1) {
            // must be tree or manifest - skip to next tree
            ObjectId subTree = getSubTreeForName(t, path[0]);
            assertNotNull(subTree, "Cannot find TREE: " + path[0]);
            assertTrue(db.hasObject(subTree), "Missing TREE: " + subTree);
            return getStreamForRelativePath(subTree, Arrays.copyOfRange(path, 1, path.length));
        } else {
            // must be blob
            ObjectId id = t.getChildren().get(new Tree.Key(path[0], EntryType.BLOB));
            assertNotNull(id, "Cannot find BLOB: " + path[0]);
            assertTrue(db.hasObject(id), "Missing BLOB: " + id);
            return db.getStream(id);
        }
    }

    private ObjectId getSubTreeForName(Tree t, String name) {
        return t.getChildren().entrySet().stream().filter(e -> e.getKey().getName().equals(name)).map(e -> {
            switch (e.getKey().getType()) {
                case MANIFEST:
                    Manifest m = lookupManifestRef(e.getValue());
                    return m.getRoot();
                case TREE:
                    return e.getValue();
                default:
                    throw new IllegalArgumentException(name + " is not a sub-tree");
            }
        }).findAny().orElse(null);
    }

    /**
     * When reading a {@link Tree}, on encountering a {@link EntryType#MANIFEST},
     * this method will convert the stored {@link ObjectId} to the root of the
     * referenced manifest.
     * <p>
     * The stored object in the {@link ObjectDatabase} must contain the serialized
     * {@link Manifest.Key}.
     *
     * @param manifestRef
     * @return
     */
    private Manifest lookupManifestRef(ObjectId manifestRef) {
        try (InputStream is = db.getStream(manifestRef)) {
            Manifest.Key key = StorageHelper.fromStream(is, Manifest.Key.class);
            if (!mdb.hasManifest(key)) {
                throw new IllegalArgumentException("Referenced manifest not found: " + key);
            }
            return mdb.getManifest(key);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot lookup manifest reference", e);
        }
    }

    /**
     * Inserts an object into the database which can be used to reference a
     * {@link Manifest} in a {@link Tree} using {@link EntryType#MANIFEST}.
     */
    public ObjectId insertManifestReference(Manifest.Key key) {
        try {
            return db.addObject(StorageHelper.toRawBytes(key));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot insert manifest reference", e);
        }
    }

    /**
     * Inserts a {@link Tree} object into the database and returns its
     * {@link ObjectId}, which can be used to build further {@link Tree} or as root
     * reference for a {@link Manifest}.
     */
    public ObjectId insertTree(Tree tree) {
        try {
            return db.addObject(StorageHelper.toRawBytes(tree));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot insert tree", e);
        }
    }

    /**
     * Checks whether a given {@link ObjectId}s backing file store is OK. In case it
     * is not, the broken file is removed from the store!
     *
     * @param id the {@link ObjectId} to check
     * @param remove whether to remove broken objects
     * @return whether the stored object in the database is OK.
     */
    public boolean checkObject(ObjectId id, boolean remove) {
        try {
            boolean ok = db.checkObject(id);
            if (!ok && remove) {
                db.removeObject(id);
            }
            return ok;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot check object consistency on " + id, e);
        }
    }

    /**
     * Load an object from the database into memory using the given loader. Helper
     * to avoid ugly stream handling code in business logic.
     *
     * @param id the {@link ObjectId} to load
     * @param loader the loader to use.
     * @return the loaded object.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadObject(ObjectId id, Function<InputStream, T> loader) {
        try {
            return (T) objectCache.get(id, () -> {
                try (InputStream is = db.getStream(id)) {
                    return loader.apply(is);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot load object " + id, e);
                }
            });
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot load object into cache: " + id, e);
        }
    }

    /**
     * Perform an operation on the actual underlying object database.
     */
    public <T> T db(DbCallable<T> c) {
        try {
            return c.call(db);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot perform DB operation", e);
        }
    }

    /**
     * Invalidates all cached data.
     */
    public void invalidateCaches() {
        this.objectCache.invalidateAll();
    }

    /**
     * Used for modifying operations on the DB.
     */
    @FunctionalInterface
    public interface DbCallable<R> {

        public R call(ObjectDatabase db) throws IOException;
    }

}
