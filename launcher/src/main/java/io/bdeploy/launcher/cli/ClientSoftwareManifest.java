package io.bdeploy.launcher.cli;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;

/**
 * A meta manifest that stores the software that is required for each installed application.
 */
public class ClientSoftwareManifest {

    private static final Logger log = LoggerFactory.getLogger(ClientSoftwareManifest.class);

    private static final String MANIFEST_PREFIX = "meta/clientSoftware/";
    private static final String FILE_NAME = "software.json";

    private final BHive hive;

    public ClientSoftwareManifest(BHive hive) {
        this.hive = hive;
    }

    /**
     * Returns the newest key that is available for the given application.
     */
    public Manifest.Key getNewestKey(String appId) {
        String manifestName = MANIFEST_PREFIX + appId;
        Optional<Long> max = hive.execute(new ManifestMaxIdOperation().setManifestName(manifestName));
        if (!max.isPresent()) {
            return null;
        }
        return new Manifest.Key(manifestName, max.get().toString());
    }

    /**
     * Returns the newest version of the software configuration for the given application and optionally creates a new one if
     * nothing is stored or if the stored entry is broken.
     */
    public ClientSoftwareConfiguration readNewest(String appId, boolean createNew) {
        Key key = getNewestKey(appId);
        ClientSoftwareConfiguration config = null;
        if (key != null) {
            config = read(key);
        }
        if (config == null && createNew) {
            config = new ClientSoftwareConfiguration();
        }
        return config;
    }

    /**
     * @return the current version of the {@link Manifest} or {@code null} if none yet
     */
    public ClientSoftwareConfiguration read(Manifest.Key key) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(FILE_NAME))) {
            return StorageHelper.fromStream(is, ClientSoftwareConfiguration.class);
        } catch (Exception e) {
            log.error("Failed to read software configuration '" + key + "'", e);
            return null;
        }
    }

    /**
     * Returns the latest version of each client software configuration. Manifest entries that are broken are ignored and will not
     * be returned.
     */
    public Collection<ClientSoftwareConfiguration> list() {
        Collection<ClientSoftwareConfiguration> result = new ArrayList<>();
        Set<Key> keys = hive.execute(new ManifestListOperation().setManifestName(MANIFEST_PREFIX));
        for (Key key : keys) {
            ClientSoftwareConfiguration software = read(key);
            if (software != null) {
                result.add(software);
            }
        }
        return result;
    }

    /**
     * Returns all client software manifests that are broken and cannot be read.
     */
    public Collection<Manifest.Key> listBroken() {
        Collection<Manifest.Key> result = new ArrayList<>();
        Set<Key> keys = hive.execute(new ManifestListOperation().setManifestName(MANIFEST_PREFIX));
        for (Key key : keys) {
            ClientSoftwareConfiguration software = read(key);
            if (software == null) {
                result.add(key);
            }
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
     * Returns a collection of all launchers that are referenced by the installed software. This keys
     * represent the launchers that must be retained.
     */
    public Set<Manifest.Key> getRequiredLauncherKeys() {
        Set<Manifest.Key> result = new HashSet<>();
        for (ClientSoftwareConfiguration software : list()) {
            if (software.launcher != null) {
                result.add(software.launcher);
            }
        }
        return result;
    }

    /**
     * Stores the given software configuration entry.
     */
    public void update(String appId, ClientSoftwareConfiguration config) {
        String manifestName = MANIFEST_PREFIX + appId;

        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(manifestName));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(manifestName, newId.toString()));

        try (Transaction t = hive.getTransactions().begin()) {
            ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config)));
            Tree.Builder tb = new Tree.Builder().add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB), descOid);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));

            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
        }

        hive.execute(new ManifestDeleteOldByIdOperation().setToDelete(manifestName).setAmountToKeep(1));
    }

    /**
     * Removes the given manifest entry
     */
    public void remove(Manifest.Key key) {
        hive.execute(new ManifestDeleteOperation().setToDelete(key));
    }

    /**
     * Removes all manifest entries of the given application
     */
    public boolean remove(String appId) {
        String manifestName = MANIFEST_PREFIX + appId;
        Set<Key> keys = hive.execute(new ManifestListOperation().setManifestName(manifestName));
        if (keys.isEmpty()) {
            return false;
        }
        for (Manifest.Key key : keys) {
            hive.execute(new ManifestDeleteOperation().setToDelete(key));
        }
        return true;
    }
}
