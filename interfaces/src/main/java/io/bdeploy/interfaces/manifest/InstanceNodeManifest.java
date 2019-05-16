package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;

public class InstanceNodeManifest {

    public static final String INSTANCE_NODE_LABEL = "X-InstanceNode";
    public static final String MANIFEST_TREE = "manifests";

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
        private final SortedSet<Manifest.Key> applications = new TreeSet<>();
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

            // grab all required manifests from the applications
            LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
            for (ApplicationConfiguration app : cfg.applications) {
                applications.add(app.application);
                ApplicationManifest amf = ApplicationManifest.of(hive, app.application);

                // applications /must/ follow the ScopedManifestKey rules.
                ScopedManifestKey smk = ScopedManifestKey.parse(app.application);

                // the dependency must be here. it has been pushed here with the product,
                // since the product /must/ reference all direct dependencies.
                applications.addAll(localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));
            }

            Tree.Builder mtb = new Tree.Builder();
            for (Manifest.Key ref : applications) {
                String refName = ref.directoryFriendlyName();
                mtb.add(new Tree.Key(refName, Tree.EntryType.MANIFEST),
                        hive.execute(new InsertManifestRefOperation().setManifest(ref)));
            }
            tb.add(new Tree.Key(MANIFEST_TREE, Tree.EntryType.TREE),
                    hive.execute(new InsertArtificialTreeOperation().setTree(mtb)));

            Manifest.Builder mfb = new Manifest.Builder(key);
            mfb.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tb)));
            mfb.addLabel(INSTANCE_NODE_LABEL, cfg.uuid);

            hive.execute(new InsertManifestOperation().addManifest(mfb.build()));
            return key;
        }

    }
}
