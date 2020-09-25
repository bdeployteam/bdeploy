package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributes;

/**
 * Describes the instance group that a named hive is associated with.
 */
public class InstanceGroupManifest {

    private static final String MANIFEST_NAME = "meta/instance-group";
    private final BHive hive;

    public InstanceGroupManifest(BHive hive) {
        this.hive = hive;
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
     * @return the current version of the {@link InstanceGroupConfiguration}, or <code>null</code> if none yet
     */
    public InstanceGroupConfiguration read() {
        Key key = getKey();
        if (key == null) {
            return null;
        }

        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(
                new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(InstanceGroupConfiguration.FILE_NAME))) {
            return StorageHelper.fromStream(is, InstanceGroupConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load instance group descriptor from: " + mf.getKey(), e);
        }
    }

    /**
     * @param desc updated customer metadata to write.
     */
    public void update(InstanceGroupConfiguration desc) {
        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(MANIFEST_NAME));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(MANIFEST_NAME, newId.toString()));

        ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(desc)));
        Tree.Builder tb = new Tree.Builder().add(new Tree.Key(InstanceGroupConfiguration.FILE_NAME, Tree.EntryType.BLOB),
                descOid);

        if (desc.logo != null) {
            // create a dummy reference to keep the object safe from garbage collection
            tb.add(new Tree.Key("logo", EntryType.BLOB), desc.logo);
        }

        mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
        hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
    }

    public CustomAttributes getAttributes(BHiveExecution bhive) {
        return new CustomAttributes(getKey(), bhive);
    }

}
