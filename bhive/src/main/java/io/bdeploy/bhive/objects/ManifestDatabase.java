/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;

/**
 * Stores and manages {@link Manifest}s. Storage happens in files distributed in
 * a database based on name and tag of each {@link Manifest}. This allows
 * concurrent updates to the database even from different processes.
 */
public class ManifestDatabase extends LockableDatabase {

    private final Path root;

    /**
     * @param root the root path of the database, created empty if it does not yet
     *            exist
     */
    public ManifestDatabase(Path root) {
        super(root);
        this.root = root;

        if (!Files.exists(root)) {
            PathHelper.mkdirs(root);
        }
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
            Files.write(pathForKey, StorageHelper.toRawBytes(manifest));
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
        });
    }

    /**
     * @return all {@link Key}s found in the database's filesystem.
     */
    public SortedSet<Manifest.Key> getAllManifests() {
        // structure is dir:root/dir:name/dir:name/file:tag
        return collectManifests(root);
    }

    private SortedSet<Manifest.Key> collectManifests(Path scanRoot) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        try {
            if (!Files.isDirectory(scanRoot)) {
                // none exist in the given namespace.
                return result;
            }

            Files.walk(scanRoot).filter(Files::isRegularFile).forEach(f -> {
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
    public SortedSet<Manifest.Key> getAllForName(String name) {
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
        if (!hasManifest(key)) {
            throw new IllegalArgumentException("Don't have manifest " + key);
        }
        try (InputStream is = Files.newInputStream(getPathForKey(key))) {
            return StorageHelper.fromStream(is, Manifest.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read manifest " + key, e);
        }
    }

}
