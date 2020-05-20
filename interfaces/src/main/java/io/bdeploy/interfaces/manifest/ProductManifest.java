package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

/**
 * A special manifestation of a {@link Manifest} which must follow a certain layout and groups multiple applications together
 * which are deployed via an 'instance' to for a version-consistent bundle.
 */
public class ProductManifest {

    private final SortedSet<Manifest.Key> applications;
    private final SortedSet<Manifest.Key> references;
    private final String prodName;
    private final ProductDescriptor desc;
    private final Manifest manifest;
    private final ObjectId cfgTreeId;
    private final List<ObjectId> plugins;

    private ProductManifest(String name, Manifest manifest, SortedSet<Manifest.Key> applications,
            SortedSet<Manifest.Key> references, ProductDescriptor desc, ObjectId cfgTreeId, List<ObjectId> plugins) {
        this.prodName = name;
        this.manifest = manifest;
        this.applications = applications;
        this.references = references;
        this.desc = desc;
        this.cfgTreeId = cfgTreeId;
        this.plugins = plugins;
    }

    /**
     * @param hive the {@link BHive} to read from
     * @param manifest the {@link Manifest} which represents the {@link ProductManifest}
     * @return a {@link ProductManifest} or <code>null</code> if the given {@link Manifest} is not a {@link ProductManifest}.
     */
    public static ProductManifest of(BHive hive, Manifest.Key manifest) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(manifest));
        String label = mf.getLabels().get(ProductManifestBuilder.PRODUCT_LABEL);
        if (label == null) {
            return null;
        }

        SortedSet<Key> allRefs = new TreeSet<>(
                hive.execute(new ManifestRefScanOperation().setManifest(manifest).setMaxDepth(2)).values());

        SortedSet<Key> appRefs = new TreeSet<>();
        SortedSet<Key> otherRefs = new TreeSet<>();

        for (Manifest.Key ref : allRefs) {
            TreeView tv = hive.execute(new ScanOperation().setMaxDepth(1).setFollowReferences(false).setManifest(ref));
            if (tv.getChildren().containsKey(ApplicationDescriptor.FILE_NAME)) {
                appRefs.add(ref);
            } else {
                // not an application
                otherRefs.add(ref);
            }
        }

        Tree tree = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        ObjectId djId = tree.getNamedEntry(ProductManifestBuilder.PRODUCT_DESC).getValue();

        ProductDescriptor desc;
        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(djId))) {
            desc = StorageHelper.fromStream(is, ProductDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load deployment manifest", e);
        }

        ObjectId cfgEntry = null;
        Map<Tree.Key, ObjectId> entries = tree.getChildren();
        Tree.Key configKey = new Tree.Key(ProductManifestBuilder.CONFIG_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(configKey)) {
            cfgEntry = entries.get(configKey);
        }

        List<ObjectId> plugins = new ArrayList<>();
        Tree.Key pluginKey = new Tree.Key(ProductManifestBuilder.PLUGINS_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(pluginKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(pluginKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".jar")) {
                    plugins.add(b.getElementId());
                }
            }).build());
        }

        return new ProductManifest(label, mf, appRefs, otherRefs, desc, cfgEntry, plugins);
    }

    /**
     * @return the tree ID of the config file template folder.
     */
    public ObjectId getConfigTemplateTreeId() {
        return cfgTreeId;
    }

    /**
     * @return a list of plugin files discovered in the products plugin folder.
     */
    public List<ObjectId> getPlugins() {
        return plugins;
    }

    /**
     * @return the name of the product.
     */
    public String getProduct() {
        return prodName;
    }

    /**
     * @return the product's descriptor.
     */
    public ProductDescriptor getProductDescriptor() {
        return desc;
    }

    /**
     * @return the product {@link Manifest} {@link Key}.
     */
    public Key getKey() {
        return manifest.getKey();
    }

    public Map<String, String> getLabels() {
        return manifest.getLabels();
    }

    /**
     * @return the applications grouped by this product
     */
    public SortedSet<Manifest.Key> getApplications() {
        return applications;
    }

    /**
     * @return additionally referenced non-application manifests (e.g. dependencies of applications).
     */
    public SortedSet<Manifest.Key> getReferences() {
        return references;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link ProductManifest}s.
     * @return a {@link SortedSet} with all available {@link ProductManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        SortedSet<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());
        for (Manifest.Key key : allKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

}
