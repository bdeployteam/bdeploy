package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.minion.MinionConfiguration;

/**
 * Describes which nodes are known by the master (including himself).
 */
public class MinionManifest {

    public static final String MANIFEST_NAME = "meta/minions";
    private static final String FILE_NAME = "minion.json";

    private final BHive hive;

    public MinionManifest(BHive hive) {
        this.hive = hive;
    }

    /**
     * Loads and returns the latest version of the minion configuration
     */
    public static MinionConfiguration getConfiguration(BHive bhive) {
        MinionManifest manifest = new MinionManifest(bhive);
        return manifest.read();
    }

    /**
     * @return the {@link Key} of the latest version of the {@link Manifest}.
     */
    public Manifest.Key getKey() {
        Optional<Long> max = hive.execute(new ManifestMaxIdOperation().setManifestName(MANIFEST_NAME));
        if (!max.isPresent()) {
            return null;
        }
        return new Manifest.Key(MANIFEST_NAME, max.get().toString());
    }

    /**
     * @return the current version of the {@link MinionConfiguration}, or <code>null</code> if none yet
     */
    public MinionConfiguration read() {
        Key key = getKey();
        if (key == null) {
            return null;
        }
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(FILE_NAME))) {
            return StorageHelper.fromStream(is, MinionConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load minion configuration from: " + mf.getKey(), e);
        }
    }

    /**
     * @param config the configuration to write.
     */
    public void update(MinionConfiguration config) {
        try (Transaction t = hive.getTransactions().begin()) {
            Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(MANIFEST_NAME));
            Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(MANIFEST_NAME, newId.toString()));

            ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config)));
            Tree.Builder tb = new Tree.Builder().add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB), descOid);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
        }
    }

}
