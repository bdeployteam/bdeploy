package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryManager;
import io.bdeploy.interfaces.manifest.state.InstanceState;

public class InstanceNodeManifest {

    public static final String INSTANCE_NODE_LABEL = "X-InstanceNode";

    private InstanceNodeConfiguration config;
    private Manifest.Key key;

    public static InstanceNodeManifest of(BHive hive, Manifest.Key key) {
        InstanceNodeManifest result = new InstanceNodeManifest();
        result.key = key;
        result.config = loadDeploymentConfiguration(hive, key);

        return result;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link InstanceNodeManifest}s.
     * @return a {@link SortedSet} with all available {@link InstanceNodeManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        SortedSet<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());
        for (Manifest.Key key : allKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(INSTANCE_NODE_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

    public InstanceNodeConfiguration getConfiguration() {
        return config;
    }

    /**
     * @return the UUID of the manifest.
     */
    public String getUUID() {
        return config.uuid;
    }

    /**
     * @param hive the {@link BHiveExecution} to operate on.
     * @return the {@link InstanceState} fot this {@link InstanceNodeManifest}. State needs to be tracked both on the
     *         {@link InstanceManifest} (master) and the {@link InstanceNodeManifest} (node).
     */
    public InstanceState getState(BHiveExecution hive) {
        return new InstanceState(getKey(), hive);
    }

    public MinionRuntimeHistoryManager getRuntimeHistory(BHiveExecution hive) {
        return new MinionRuntimeHistoryManager(getKey(), hive);
    }

    /**
     * @return the underlying {@link Manifest} {@link Key}.
     */
    public Manifest.Key getKey() {
        return key;
    }

    private static InstanceNodeConfiguration loadDeploymentConfiguration(BHive hive, Manifest.Key key) {
        Manifest manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        Tree tree = hive.execute(new TreeLoadOperation().setTree(manifest.getRoot()));
        ObjectId djId = tree.getNamedEntry(InstanceNodeConfiguration.FILE_NAME).getValue();

        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(djId))) {
            return StorageHelper.fromStream(is, InstanceNodeConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load deployment manifest", e);
        }
    }

    public static class Builder {

        private String name;
        private Manifest.Key key;
        private InstanceNodeConfiguration cfg;
        private ObjectId configTree;

        public Builder setMinionName(String name) {
            this.name = name;
            return this;
        }

        public Builder setInstanceNodeConfiguration(InstanceNodeConfiguration cfg) {
            this.cfg = cfg;
            return this;
        }

        public Builder setConfigTreeId(ObjectId configTree) {
            this.configTree = configTree;
            return this;
        }

        /**
         * Explicitly set the key under which to persist this manifest - use with care!
         */
        public Builder setKey(Manifest.Key key) {
            this.key = key;
            return this;
        }

        public Manifest.Key insert(BHive hive) {
            RuntimeAssert.assertNotNull(name, "Name not set");
            RuntimeAssert.assertNotNull(cfg, "Configuration not set");

            String mfName = cfg.uuid + "/" + name;

            if (key == null) {
                key = new Manifest.Key(mfName, hive.execute(new ManifestNextIdOperation().setManifestName(mfName)).toString());
            }

            ObjectId cfgId = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(cfg)));
            Tree.Builder tb = new Tree.Builder();
            tb.add(new Tree.Key(InstanceNodeConfiguration.FILE_NAME, Tree.EntryType.BLOB), cfgId);

            if (configTree != null) {
                tb.add(new Tree.Key("config", Tree.EntryType.TREE), configTree);
            }

            Manifest.Builder mfb = new Manifest.Builder(key);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
            mfb.addLabel(INSTANCE_NODE_LABEL, cfg.uuid);

            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
            return key;
        }

    }
}
