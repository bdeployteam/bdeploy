/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

import io.bdeploy.bhive.ManifestSpawnListener;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.PathHelper;

/**
 * Stores and manages {@link Manifest}s. Storage happens in files distributed in
 * a database based on name and tag of each {@link Manifest}. This allows
 * concurrent updates to the database even from different processes.
 */
public class ManifestDatabase extends LockableDatabase implements AutoCloseable {

    private final static Logger log = LoggerFactory.getLogger(ManifestDatabase.class);

    private final Path root;
    private final Path tmp;

    private final List<ManifestSpawnListener> listeners = new ArrayList<>();

    private ScheduledFuture<?> schedNotify;
    private final List<Manifest.Key> added = new ArrayList<>();
    private final ScheduledExecutorService notify = Executors
            .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Manifest DB Notifier"));

    /**
     * A cache for Manifest objects which need to actually be loaded from disk.
     * <p>
     * Assuming a max object size of ~4K (manifest includes cached references), this cache would grow to ~10MB.
     */
    private final Cache<Manifest.Key, Manifest> manifestCache = CacheBuilder.newBuilder().maximumSize(2_500).build();

    /**
     * @param root the root path of the database, created empty if it does not yet
     *            exist
     */
    public ManifestDatabase(Path root) {
        super(root);
        this.root = root;
        this.tmp = root.resolve(".tmp");

        if (!Files.exists(root)) {
            PathHelper.mkdirs(root);
        }

        if (!Files.exists(tmp)) {
            PathHelper.mkdirs(tmp);
        }
    }

    @Override
    public void close() {
        notify.shutdownNow();
    }

    public void addSpawnListener(ManifestSpawnListener listener) {
        listeners.add(listener);
    }

    public void removeSpawnListener(ManifestSpawnListener listener) {
        listeners.remove(listener);
    }

    public void scheduleNotify(Manifest.Key key) {
        if (listeners.isEmpty()) {
            return;
        }

        synchronized (added) {
            if (log.isDebugEnabled()) {
                log.debug("Adding {} to notify queue", key);
            }
            added.add(key);
        }

        if (schedNotify != null && !schedNotify.isDone()) {
            if (log.isDebugEnabled()) {
                log.debug("Cancel existing scheduled notify");
            }
            schedNotify.cancel(false);
        }

        schedNotify = notify.schedule(() -> {
            List<Manifest.Key> copy;
            synchronized (added) {
                if (log.isDebugEnabled()) {
                    log.debug("Notify {} listeners: {}", listeners.size(), added);
                }
                copy = new ArrayList<>(added);
                added.clear();
            }
            for (ManifestSpawnListener listener : listeners) {
                try {
                    listener.spawn(copy);
                } catch (Exception e) {
                    log.warn("Exception in manifest listener", e);
                }
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * @param key the manifest key
     * @return the {@link Path} to the file storing the actual {@link Manifest}.
     */
    private Path getPathForKey(Manifest.Key key) {
        return root.resolve(key.getName()).resolve(key.getTag());
    }

    /**
     * @param key the manifest key to check
     * @return whether the {@link Manifest} exists in the database.
     */
    public boolean hasManifest(Manifest.Key key) {
        return Files.exists(getPathForKey(key));
    }

    /**
     * Concurrent-save adds a {@link Manifest} to the database.
     *
     * @param manifest the manifest to store in the database.
     */
    public void addManifest(Manifest manifest) {
        locked(() -> {
            if (hasManifest(manifest.getKey())) {
                throw new IllegalArgumentException("Manifest " + manifest.getKey() + " already present.");
            }
            Path pathForKey = getPathForKey(manifest.getKey());
            PathHelper.mkdirs(pathForKey.getParent());

            // unfortunately there is no better way to detect a 'ZipPath' as the class is not accessible directly.
            if (pathForKey.getClass().getSimpleName().contains("Zip")) {
                // in case of ZIP files we cannot move afterwards, so we need to write directly
                Files.write(pathForKey, StorageHelper.toRawBytes(manifest), StandardOpenOption.SYNC, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Path tmpFile = Files.createTempFile(tmp, "mf-", ".tmp");
                try {
                    Files.write(tmpFile, StorageHelper.toRawBytes(manifest), StandardOpenOption.SYNC, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    Files.move(tmpFile, pathForKey, StandardCopyOption.ATOMIC_MOVE);
                } catch (Throwable t) {
                    PathHelper.deleteRecursive(tmpFile);
                    throw t;
                }
            }
            manifestCache.put(manifest.getKey(), manifest);
            scheduleNotify(manifest.getKey());
        });
    }

    /**
     * Concurrent-save removes a manifest from the database. Does not remove
     * underlying objects from any {@link ObjectDatabase}, just removes the manifest
     * entry (which is the "root-anchor" to those objects).
     *
     * @param key the manifest to remove
     */
    public void removeManifest(Manifest.Key key) {
        locked(() -> {
            Files.deleteIfExists(getPathForKey(key));
            manifestCache.invalidate(key);
        });
    }

    /**
     * @return all {@link Key}s found in the database's filesystem.
     */
    public Set<Manifest.Key> getAllManifests() {
        // structure is dir:root/dir:name/dir:name/file:tag
        return collectManifests(root);
    }

    private Set<Manifest.Key> collectManifests(Path scanRoot) {
        Set<Manifest.Key> result = new TreeSet<>();
        try {
            long xctpCount = 0;
            do {
                if (!Files.isDirectory(scanRoot)) {
                    // none exist in the given namespace.
                    return result;
                }

                try (Stream<Path> walk = Files.walk(scanRoot)) {
                    walk.filter(Files::isRegularFile).forEach(f -> {
                        if (f.startsWith(this.tmp)) {
                            return;
                        }

                        Path rel = root.relativize(f);

                        if (rel.getParent() == null) {
                            // file in the db-root, cannot be a manifest.
                            return;
                        }

                        // windows paths contain '\' - replace it to get proper names.
                        String manifestName = rel.getParent().toString().replace('\\', '/');
                        String manifestTag = rel.getFileName().toString();

                        result.add(new Manifest.Key(manifestName, manifestTag));
                    });
                    return result;
                } catch (UncheckedIOException | NoSuchFileException e) {
                    // something was removed in the middle of the walk... retry.
                    if (!(e instanceof NoSuchFileException || e.getCause() instanceof NoSuchFileException || xctpCount++ > 10)) {
                        throw e;
                    }
                }
            } while (true);
        } catch (IOException e) {
            throw new IllegalStateException("Error reading manifest database", e);
        }
    }

    /**
     * @param name the name to collect for. May be any number of name segments (must be complete segments). e.g 'my/manifest' will
     *            match the same (but potentially more) than 'my/manifest/name', but none of the two will match 'my/manifestname'.
     *            If the name includes a ':', it is treated as a fully qualified manifest key.
     * @return the list of manifests which matched the name.
     */
    public Set<Manifest.Key> getAllForName(String name) {
        if (name.contains(":")) {
            // if the name is fully qualified, it is either there or not.
            SortedSet<Manifest.Key> result = new TreeSet<>();
            Manifest.Key key = Manifest.Key.parse(name);
            if (hasManifest(key)) {
                result.add(key);
            }
            return result;
        }
        Path namedRoot = root.resolve(name);
        return collectManifests(namedRoot);
    }

    /**
     * @param key the key of the manifest to load
     * @return the {@link Manifest} loaded from its backing file.
     */
    public Manifest getManifest(Manifest.Key key) {
        try {
            return manifestCache.get(key, () -> {
                if (!hasManifest(key)) {
                    throw new IllegalArgumentException("Don't have manifest " + key);
                }
                try (InputStream is = Files.newInputStream(getPathForKey(key))) {
                    return StorageHelper.fromStream(is, Manifest.class);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read manifest " + key, e);
                }
            });
        } catch (ExecutionException | UncheckedExecutionException e) {
            throw new IllegalStateException("Cannot load manifest into cache: " + key, e);
        }
    }

    /**
     * Invalidates all cached data.
     */
    public void invalidateCaches() {
        this.manifestCache.invalidateAll();
    }

}
