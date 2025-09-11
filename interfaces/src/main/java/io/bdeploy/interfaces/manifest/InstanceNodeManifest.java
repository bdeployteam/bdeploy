package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
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
import io.bdeploy.interfaces.nodes.NodeType;

public class InstanceNodeManifest {

    public static final String INSTANCE_NODE_LABEL = "X-InstanceNode";
    public static final String ROOT_CONFIG_NAME = "root";

    private static final String CONFIG_TREE_NAME = "config";

    private InstanceNodeConfiguration config;
    private Manifest.Key key;
    private Map<String, ObjectId> configTrees;

    public static InstanceNodeManifest of(BHive hive, Manifest.Key key) {
        InstanceNodeManifest result = new InstanceNodeManifest();
        result.key = key;
        result.config = loadDeploymentConfiguration(hive, key);
        result.configTrees = loadConfigTrees(hive, key);

        // Explicitly set the node type so that new servers can still function with old nodes
        if (result.config.nodeType == null) {
            String name = key.getName();
            result.config.nodeType = name.substring(name.lastIndexOf("/") + 1).equals(InstanceManifest.CLIENT_NODE_NAME)
                    ? NodeType.CLIENT
                    : NodeType.SERVER;
        }

        return result;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link InstanceNodeManifest}s.
     * @return a {@link SortedSet} with all available {@link InstanceNodeManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHiveExecution hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());
        for (Manifest.Key key : allKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key).setNullOnError(true));
            if (mf != null && mf.getLabels().containsKey(INSTANCE_NODE_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

    public InstanceNodeConfiguration getConfiguration() {
        return config;
    }

    /**
     * @return the ID of the manifest.
     */
    public String getId() {
        return config.id;
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

    /**
     * @return a map of named config trees.
     * @see Builder#addConfigTreeId(String, ObjectId)
     */
    public Map<String, ObjectId> getConfigTrees() {
        return configTrees;
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

    private static Map<String, ObjectId> loadConfigTrees(BHive hive, Key key) {
        Manifest manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        Tree tree = hive.execute(new TreeLoadOperation().setTree(manifest.getRoot()));

        Map<String, ObjectId> result = new TreeMap<>();

        Optional<Entry<Tree.Key, ObjectId>> namedEntry = tree.getChildren().entrySet().stream()
                .filter(e -> CONFIG_TREE_NAME.equals(e.getKey().getName()) && e.getKey().getType() == EntryType.TREE).findFirst();

        if (namedEntry.isPresent()) {
            // yay, we have a config tree - populate the map.
            Tree cfgTree = hive.execute(new TreeLoadOperation().setTree(namedEntry.get().getValue()));
            for (var entry : cfgTree.getChildren().entrySet()) {
                result.put(entry.getKey().getName(), entry.getValue());
            }
        }

        return result;
    }

    public static class Builder {

        private String name;
        private Manifest.Key key;
        private InstanceNodeConfiguration cfg;
        private final Map<String, ObjectId> configTrees = new TreeMap<>();

        public Builder setMinionName(String name) {
            this.name = name;
            return this;
        }

        public Builder setInstanceNodeConfiguration(InstanceNodeConfiguration cfg) {
            this.cfg = cfg;
            return this;
        }

        /**
         * @param name either {@link InstanceNodeManifest#ROOT_CONFIG_NAME} or the ID of an application
         * @param configTree the dedicated config file tree used for this name.
         * @return this for chaining.
         */
        public Builder addConfigTreeId(String name, ObjectId configTree) {
            this.configTrees.put(name, configTree);
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
            try (Transaction t = hive.getTransactions().begin()) {
                return doInsertLocked(hive);
            }
        }

        private Manifest.Key doInsertLocked(BHive hive) {
            RuntimeAssert.assertNotNull(name, "Name not set");
            RuntimeAssert.assertNotNull(cfg, "Configuration not set");

            String mfName = cfg.id + "/" + name;

            if (key == null) {
                key = new Manifest.Key(mfName, hive.execute(new ManifestNextIdOperation().setManifestName(mfName)).toString());
            }

            ObjectId cfgId = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(cfg)));
            Tree.Builder tb = new Tree.Builder();
            tb.add(new Tree.Key(InstanceNodeConfiguration.FILE_NAME, Tree.EntryType.BLOB), cfgId);

            // there may be multiple config trees in case of client nodes. servers only use a single 'root' tree.
            // note that the DCU exports the complete manifest to disc, so changes in paths here need to be
            // respected in DeploymentPathProvider!
            // for client applications, the trees are actually read back and inspected later on.
            if (!configTrees.isEmpty()) {
                Tree.Builder cfgT = new Tree.Builder();
                for (Map.Entry<String, ObjectId> configEntry : configTrees.entrySet()) {
                    if (configEntry.getValue() != null) {
                        cfgT.add(new Tree.Key(configEntry.getKey(), Tree.EntryType.TREE), configEntry.getValue());
                    }
                }
                tb.add(new Tree.Key(CONFIG_TREE_NAME, Tree.EntryType.TREE),
                        hive.execute(new InsertArtificialTreeOperation().setTree(cfgT)));
            }

            Manifest.Builder mfb = new Manifest.Builder(key);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
            mfb.addLabel(INSTANCE_NODE_LABEL, cfg.id);

            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
            return key;
        }

    }
}
