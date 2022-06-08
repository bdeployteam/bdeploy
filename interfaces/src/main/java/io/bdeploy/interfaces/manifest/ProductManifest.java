package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.PersistentManifestClassification;
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
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCache;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCacheRecord;

/**
 * A special manifestation of a {@link Manifest} which must follow a certain layout and groups multiple applications together
 * which are deployed via an 'instance' to for a version-consistent bundle.
 */
public class ProductManifest {

    private static final Logger log = LoggerFactory.getLogger(ProductManifest.class);

    private final SortedSet<Manifest.Key> applications;
    private final SortedSet<Manifest.Key> references;
    private final String prodName;
    private final ProductDescriptor desc;
    private final Manifest manifest;
    private final ObjectId cfgTreeId;
    private final List<ObjectId> plugins;
    private final List<InstanceTemplateDescriptor> instanceTemplates;
    private final List<ApplicationTemplateDescriptor> applicationTemplates;

    private ProductManifest(String name, Manifest manifest, SortedSet<Manifest.Key> applications,
            SortedSet<Manifest.Key> references, ProductDescriptor desc, ObjectId cfgTreeId, List<ObjectId> plugins,
            List<InstanceTemplateDescriptor> instanceTemplates, List<ApplicationTemplateDescriptor> applicationTemplates) {
        this.prodName = name;
        this.manifest = manifest;
        this.applications = applications;
        this.references = references;
        this.desc = desc;
        this.cfgTreeId = cfgTreeId;
        this.plugins = plugins;
        this.instanceTemplates = instanceTemplates;
        this.applicationTemplates = applicationTemplates;
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

        ProductManifestStaticCache cacheStorage = new ProductManifestStaticCache(manifest, hive);
        ProductManifestStaticCacheRecord cached = cacheStorage.read();

        if (cached != null) {
            return new ProductManifest(label, mf, cached.appRefs, cached.otherRefs, cached.desc, cached.cfgEntry, cached.plugins,
                    cached.templates, cached.applicationTemplates);
        }

        SortedSet<Key> allRefs = new TreeSet<>(
                hive.execute(new ManifestRefScanOperation().setManifest(manifest).setMaxDepth(2)).values());

        SortedSet<Key> appRefs = new TreeSet<>();
        SortedSet<Key> otherRefs = new TreeSet<>();

        for (Manifest.Key ref : allRefs) {
            TreeView tv = hive.execute(new ScanOperation().setMaxDepth(1).setFollowReferences(false).setManifest(ref));
            if (tv.getChildren().containsKey(ApplicationDescriptorApi.FILE_NAME)) {
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

        List<InstanceTemplateDescriptor> templates = new ArrayList<>();
        Tree.Key templateKey = new Tree.Key(ProductManifestBuilder.TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(templateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(templateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        templates.add(StorageHelper.fromYamlStream(is, InstanceTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load instance template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }
        templates.sort((a, b) -> a.name.compareTo(b.name));

        List<ApplicationTemplateDescriptor> applicationTemplates = new ArrayList<>();
        Tree.Key appTemplateKey = new Tree.Key(ProductManifestBuilder.APP_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(appTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(appTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        applicationTemplates.add(StorageHelper.fromYamlStream(is, ApplicationTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load application template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        // lazy, DFS resolving of all templates.
        resolveTemplates(templates, applicationTemplates);
        applicationTemplates.sort((a, b) -> a.name.compareTo(b.name));

        // store persistent information.
        try {
            cacheStorage.store(appRefs, otherRefs, desc, cfgEntry, plugins, templates, applicationTemplates);
        } catch (Exception e) {
            // there is a chance for a race condition here, which actually does not do any harm (except for a
            // tiny performance hit since two threads calculate this). in case two threads try to persist the
            // exact same thing, we simply ignore the failure.
            if (log.isDebugEnabled()) {
                log.debug("Cannot store persistent cache for {}: {}", manifest, e.toString());
            }
        }

        return new ProductManifest(label, mf, appRefs, otherRefs, desc, cfgEntry, plugins, templates, applicationTemplates);
    }

    private static void resolveTemplates(List<InstanceTemplateDescriptor> instTemplates,
            List<ApplicationTemplateDescriptor> appTemplates) {
        for (var itd : instTemplates) {
            for (var group : itd.groups) {
                for (var app : group.applications) {
                    try {
                        resolveAppTemplate(app, appTemplates, itd.variables);
                    } catch (Exception e) {
                        log.error("Cannot resolve application template {} from instance template {}", app.name, itd.name, e);
                    }
                }

                // remove unresolved things.
                group.applications.removeIf(a -> !a.resolved);
            }

            // remove group if no application resolved.
            itd.groups.removeIf(g -> g.applications.isEmpty());
        }

        // remove template if no application in any group resolved.
        instTemplates.removeIf(t -> t.groups.isEmpty());

        for (ApplicationTemplateDescriptor atd : appTemplates) {
            try {
                resolveAppTemplate(atd, appTemplates, atd.variables);
            } catch (Exception e) {
                log.error("Cannot resolve application template {}", atd.name, e);
            }
        }

        appTemplates.removeIf(a -> !a.resolved);
    }

    private static void resolveAppTemplate(TemplateApplication app, List<ApplicationTemplateDescriptor> appTemplates,
            List<TemplateVariable> varList) {
        if (app.resolved) {
            return;
        }

        if (app.template == null && app.application == null) {
            // unfortunately no more information available...
            throw new IllegalArgumentException("Template without application and template reference found");
        }

        if (app.template != null) {
            var parent = appTemplates.stream().filter(t -> app.template.equals(t.id)).findFirst();
            if (!parent.isPresent()) {
                if (log.isDebugEnabled()) {
                    log.debug("Template error. Cannot find template {}", app.template);
                }
                return;
            }
            var parentDesc = parent.get();
            if (!parentDesc.resolved) {
                resolveAppTemplate(parentDesc, appTemplates, parentDesc.variables);
            }

            // add variables from parent template to next outer variables.
            for (var variable : parentDesc.variables) {
                var existing = varList.stream().filter(v -> v.uid.equals(variable.uid)).findAny();
                if (!existing.isPresent()) {
                    varList.add(variable);
                }
            }

            // merge all kinds of attributes, so that 'app' contains a complete template in the end.
            mergeParentIntoTemplate(app, parentDesc);
        }

        app.resolved = true;
    }

    private static void mergeParentIntoTemplate(TemplateApplication app, ApplicationTemplateDescriptor parentDesc) {
        // merge simple attributes
        app.application = resolveStringValue(app.application, parentDesc.application);
        app.name = resolveStringValue(app.name, parentDesc.name);
        app.description = resolveStringValue(app.description, parentDesc.description);
        app.preferredProcessControlGroup = resolveStringValue(app.preferredProcessControlGroup,
                parentDesc.preferredProcessControlGroup);

        // merge process control partial object as map
        for (var entry : parentDesc.processControl.entrySet()) {
            if (!app.processControl.containsKey(entry.getKey())) {
                app.processControl.put(entry.getKey(), entry.getValue());
            }
        }

        if (app.startParameters == null) {
            app.startParameters = new ArrayList<>();
        }

        if (parentDesc.startParameters != null) {
            // merge start parameters
            for (var param : parentDesc.startParameters) {
                var existing = app.startParameters.stream().filter(p -> p.uid.equals(param.uid)).findAny();
                if (!existing.isPresent()) {
                    app.startParameters.add(param);
                }
            }
        }
    }

    private static String resolveStringValue(String ours, String theirs) {
        if (ours != null) {
            return ours;
        }
        return theirs;
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
     * @return a list of instance templates which can be used to populate empty instances.
     */
    public List<InstanceTemplateDescriptor> getInstanceTemplates() {
        return instanceTemplates;
    }

    /**
     * @return a list of application templates which can be used when creating applications.
     */
    public List<ApplicationTemplateDescriptor> getApplicationTemplates() {
        return applicationTemplates;
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
     * @return the application with the given ID
     */
    public Manifest.Key getApplication(String appName) {
        for (Manifest.Key app : applications) {
            if (app.getName().equals(appName)) {
                return app;
            }
        }
        return null;
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

        // filter out internal (meta, etc.) manifests right away so we don't waste time checking.
        Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation()).stream().filter(k -> !k.getName().startsWith("."))
                .collect(Collectors.toSet());

        PersistentManifestClassification<ProductClassification> pc = new PersistentManifestClassification<>(hive, "products",
                m -> new ProductClassification(m.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)));

        pc.loadAndUpdate(allKeys);
        pc.getClassifications().entrySet().stream().filter(e -> e.getValue().isProduct).map(Entry::getKey).forEach(result::add);

        return result;
    }

    public static final class ProductClassification {

        public final boolean isProduct;

        @JsonCreator
        public ProductClassification(@JsonProperty("isProduct") boolean prod) {
            isProduct = prod;
        }

    }

}
