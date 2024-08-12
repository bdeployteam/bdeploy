package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;

/**
 * Manages storage and retrieval of {@link SystemConfiguration}s.
 */
public class SystemManifest {

    public static final String MANIFEST_PREFIX = "meta/system/";

    private final SystemConfiguration config;
    private final Key key;

    private SystemManifest(Manifest.Key key, SystemConfiguration config) {
        this.key = key;
        this.config = config;
    }

    /**
     * @return the stored {@link SystemConfiguration}
     */
    public SystemConfiguration getConfiguration() {
        return config;
    }

    /**
     * @return the key under which this manifest is stored in the {@link BHive}.
     */
    public Key getKey() {
        return key;
    }

    /**
     * The most current key for the {@link SystemManifest} with the given id, or <code>null</code> if no system with given id
     * exists.
     */
    private static Manifest.Key getCurrentKey(BHive hive, String systemId) {
        String name = getManifestName(systemId);
        Optional<Long> tag = hive.execute(new ManifestMaxIdOperation().setManifestName(name));

        if (!tag.isPresent()) {
            return null;
        }

        return new Manifest.Key(name, String.valueOf(tag.get()));
    }

    public static String getManifestName(String systemId) {
        return MANIFEST_PREFIX + systemId;
    }

    /**
     * Loads a specified {@link SystemManifest} version.
     */
    public static SystemManifest of(BHiveExecution hive, Manifest.Key key) {
        if (key == null) {
            return null;
        }

        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));

        if (mf == null) {
            return null;
        }

        SystemConfiguration sc;
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRelativePath(SystemConfiguration.FILE_NAME)
                .setRootTree(hive.execute(new ManifestLoadOperation().setManifest(key)).getRoot()))) {
            sc = StorageHelper.fromStream(is, SystemConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load instance configuration " + SystemConfiguration.FILE_NAME + " from " + key, e);
        }

        return new SystemManifest(key, sc);
    }

    /**
     * Loads the latest {@link SystemManifest} version with the specified system ID.
     */
    public static SystemManifest load(BHive hive, String systemId) {
        return of(hive, getCurrentKey(hive, systemId));
    }

    /**
     * Deletes all versions of the {@link SystemManifest} with the given system ID.
     */
    public static void delete(BHiveExecution hive, String systemId) {
        String name = getManifestName(systemId);
        hive.execute(new ManifestListOperation().setManifestName(name))
                .forEach(m -> hive.execute(new ManifestDeleteOperation().setToDelete(m)));
    }

    /**
     * List all {@link SystemManifest}s. Only the latest version is returned for each system.
     */
    public static SortedSet<Manifest.Key> scan(BHiveExecution hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation().setManifestName(MANIFEST_PREFIX));

        // find all manifest which look like an instance root manifest (ending in /root).
        Set<String> names = allKeys.stream().map(Manifest.Key::getName).distinct().collect(Collectors.toSet());

        for (String name : names) {
            Optional<Long> id = hive.execute(new ManifestMaxIdOperation().setManifestName(name));
            if (id.isPresent()) {
                result.add(new Manifest.Key(name, id.get().toString()));
            }
        }

        return result;
    }

    /**
     * A builder which allows to create new {@link SystemManifest} versions.
     */
    public static final class Builder {

        private SystemConfiguration config;
        private String systemId;

        public Builder setSystemId(String id) {
            this.systemId = id;
            return this;
        }

        public Builder setConfiguration(SystemConfiguration config) {
            this.config = config;
            return this;
        }

        public Manifest.Key insert(BHive hive) {
            try (Transaction t = hive.getTransactions().begin()) {
                return doInsertLocked(hive);
            }
        }

        private Manifest.Key doInsertLocked(BHive hive) {
            String name = getManifestName(systemId);
            Long tag = hive.execute(new ManifestNextIdOperation().setManifestName(name));
            Manifest.Key key = new Manifest.Key(name, String.valueOf(tag));

            Tree.Builder root = new Tree.Builder();
            root.add(new Tree.Key(SystemConfiguration.FILE_NAME, Tree.EntryType.BLOB),
                    hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config))));

            Manifest.Builder mb = new Manifest.Builder(key)
                    .setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(root)));

            hive.execute(new InsertManifestOperation().addManifest(mb.build(hive)));

            return key;
        }
    }

}
