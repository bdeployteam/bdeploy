package io.bdeploy.launcher;

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

/**
 * Contains global settings of local client applications.
 */
public class LocalClientApplicationSettingsManifest {

    private static final String MANIFEST_NAME = "meta/client-app";
    private static final String FILE_NAME = "settings";
    private final BHive hive;

    public LocalClientApplicationSettingsManifest(BHive hive) {
        this.hive = hive;
    }

    /**
     * @param settings Updated customer metadata to write
     */
    public Manifest.Key write(LocalClientApplicationSettings settings) {
        Long nextId = hive.execute(new ManifestNextIdOperation().setManifestName(MANIFEST_NAME));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(MANIFEST_NAME, nextId.toString()));

        try (Transaction t = hive.getTransactions().begin()) {
            ObjectId oid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(settings)));
            Tree.Builder tb = new Tree.Builder().add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB), oid);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
            return mfb.getKey();
        }
    }

    /**
     * @return The current version of the {@link LocalClientApplicationSettings}, or a new instance if no existing one could be
     *         found.
     */
    public LocalClientApplicationSettings read() {
        Optional<Long> currentlyActiveTag = hive.execute(new ManifestMaxIdOperation().setManifestName(MANIFEST_NAME));
        if (currentlyActiveTag.isEmpty()) {
            return new LocalClientApplicationSettings();
        }

        Key key = new Manifest.Key(MANIFEST_NAME, currentlyActiveTag.get().toString());
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));

        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(FILE_NAME))) {
            return StorageHelper.fromStream(is, LocalClientApplicationSettings.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load local client application settings " + mf.getKey(), e);
        }
    }
}
