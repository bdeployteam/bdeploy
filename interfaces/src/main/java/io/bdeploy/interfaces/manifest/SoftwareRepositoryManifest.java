package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
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
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;

/**
 * Describes a software repository.
 */
public class SoftwareRepositoryManifest {

    private static final String MANIFEST_NAME = "meta/software-repo";
    private final BHive hive;

    public SoftwareRepositoryManifest(BHive hive) {
        this.hive = hive;
    }

    public static boolean isSoftwareRepositoryManifest(Manifest.Key key) {
        return key.getName().equals(MANIFEST_NAME);
    }

    /**
     * @return the current version of the {@link SoftwareRepositoryConfiguration}, or <code>null</code> if not present
     */
    public SoftwareRepositoryConfiguration read() {
        Optional<Long> max = hive.execute(new ManifestMaxIdOperation().setManifestName(MANIFEST_NAME));
        if (!max.isPresent()) {
            return null;
        }

        Manifest mf = hive
                .execute(new ManifestLoadOperation().setManifest(new Manifest.Key(MANIFEST_NAME, max.get().toString())));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(SoftwareRepositoryConfiguration.FILE_NAME))) {
            return StorageHelper.fromStream(is, SoftwareRepositoryConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load software repository descriptor from: " + mf.getKey(), e);
        }
    }

    /**
     * @param desc updated customer metadata to write.
     */
    public void update(SoftwareRepositoryConfiguration desc) {
        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(MANIFEST_NAME));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(MANIFEST_NAME, newId.toString()));

        ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(desc)));
        Tree.Builder tb = new Tree.Builder().add(new Tree.Key(SoftwareRepositoryConfiguration.FILE_NAME, Tree.EntryType.BLOB),
                descOid);

        mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
        hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
    }

}
