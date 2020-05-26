package io.bdeploy.interfaces.plugin;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * A manifest describing a standalone (global) plugin.
 */
public class PluginManifest {

    private static final String PLUGIN_FILE = "plugin.jar";
    private static final String PLUGIN_LABEL = "X-BDeploy-Plugin";
    private static final String PLUGIN_NS = "meta/plugins/";

    private final Manifest.Key key;
    private final ObjectId plugin;

    private PluginManifest(Manifest.Key key, ObjectId plugin) {
        this.key = key;
        this.plugin = plugin;
    }

    public Manifest.Key getKey() {
        return key;
    }

    public ObjectId getPlugin() {
        return plugin;
    }

    public static Set<Manifest.Key> scan(BHive hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        SortedSet<Manifest.Key> allKeys = hive.execute(new ManifestListOperation().setManifestName(PLUGIN_NS));
        for (Manifest.Key key : allKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(PLUGIN_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

    public static PluginManifest of(BHive hive, Manifest.Key key) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
        String label = mf.getLabels().get(PLUGIN_LABEL);
        if (label == null) {
            return null;
        }

        TreeView tree = hive.execute(new ScanOperation().setTree(mf.getRoot()).setMaxDepth(1));
        ElementView ev = tree.getChildren().get(PLUGIN_FILE);
        if (ev == null) {
            throw new IllegalStateException("Plugin manifest with plugin label, but missing plugin file: " + key);
        }

        return new PluginManifest(key, ev.getElementId());
    }

    public static final class Builder {

        private byte[] data;

        public Builder setData(byte[] data) {
            this.data = data;
            return this;
        }

        public Manifest.Key insert(BHive hive) {
            RuntimeAssert.assertNotNull(data, "No plugin data set");

            ObjectId plugin = hive.execute(new ImportObjectOperation().setData(data));

            Tree.Builder builder = new Tree.Builder();
            builder.add(new Tree.Key(PLUGIN_FILE, EntryType.BLOB), plugin);

            Manifest.Key key = new Manifest.Key(PLUGIN_NS + plugin, "0");

            Manifest.Builder mf = new Manifest.Builder(key);
            mf.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(builder)));
            mf.addLabel(PLUGIN_LABEL, "yes");

            hive.execute(new InsertManifestOperation().addManifest(mf.build(hive)));

            return key;
        }

    }

}
