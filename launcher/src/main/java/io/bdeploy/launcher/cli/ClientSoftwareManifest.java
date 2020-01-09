package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;

/**
 * A meta manifest that stores the software that is required for each installed.
 */
public class ClientSoftwareManifest {

    private static final String MANIFEST_PREFIX = "meta/clientSoftware/";
    private static final String FILE_NAME = "software.json";

    private final BHive hive;

    public ClientSoftwareManifest(BHive hive) {
        this.hive = hive;
    }

    /**
     * Returns the newest key that is available for the given application.
     */
    public Manifest.Key getNewestKey(String appUid) {
        String manifestName = MANIFEST_PREFIX + appUid;
        Optional<Long> max = hive.execute(new ManifestMaxIdOperation().setManifestName(manifestName));
        if (!max.isPresent()) {
            return null;
        }
        return new Manifest.Key(manifestName, max.get().toString());
    }

    /**
     * Returns the newest version of the software configuration for the given application.
     */
    public ClientSoftwareConfiguration readNewest(String appUid) {
        Key key = getNewestKey(appUid);
        if (key == null) {
            return null;
        }
        return read(key);
    }

    /**
     * @return the current version of the {@link Manifest} or {@code null} if none yet
     */
    public ClientSoftwareConfiguration read(Manifest.Key key) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(FILE_NAME))) {
            return StorageHelper.fromStream(is, ClientSoftwareConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load software configuration from: " + mf.getKey(), e);
        }
    }

    /**
     * Returns the latest version of each client software configuration
     */
    public Collection<ClientSoftwareConfiguration> list() {
        Collection<ClientSoftwareConfiguration> result = new ArrayList<>();
        SortedSet<Key> keys = hive.execute(new ManifestListOperation().setManifestName(MANIFEST_PREFIX));
        for (Key key : keys) {
            result.add(read(key));
        }
        return result;
    }

    /**
     * Returns a collection of all manifest keys that are referenced by the installed software. This keys
     * represent the required software that must be retained in the pool.
     */
    public Set<Manifest.Key> getRequiredKeys() {
        Set<Manifest.Key> result = new HashSet<>();
        for (ClientSoftwareConfiguration software : list()) {
            result.addAll(software.requiredSoftware);
        }
        return result;
    }

    /**
     * Stores the given software configuration entry.
     */
    public void update(String appUid, ClientSoftwareConfiguration config) {
        String manifestName = MANIFEST_PREFIX + appUid;

        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(manifestName));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(manifestName, newId.toString()));

        ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config)));
        Tree.Builder tb = new Tree.Builder().add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB), descOid);
        mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));

        hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
        hive.execute(new ManifestDeleteOldByIdOperation().setToDelete(manifestName).setAmountToKeep(1));
    }

}
