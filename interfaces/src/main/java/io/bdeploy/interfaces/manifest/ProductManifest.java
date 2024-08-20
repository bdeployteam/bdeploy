package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

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
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCache;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCacheRecordV2;

/**
 * A special manifestation of a {@link Manifest} which must follow a certain layout and groups multiple applications together
 * which are deployed via an 'instance' to for a version-consistent bundle.
 */
public class ProductManifest {

    private static final Logger log = LoggerFactory.getLogger(ProductManifest.class);
    private static final Cache<BHive, SortedSet<Manifest.Key>> SCAN_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).softValues().build();

    private final SortedSet<Manifest.Key> applications;
    private final SortedSet<Manifest.Key> references;
    private final String prodName;
    private final ProductDescriptor desc;
    private final Manifest manifest;
    private final ObjectId cfgTreeId;
    private final List<ObjectId> plugins;
    private final List<FlattenedInstanceTemplateConfiguration> instanceTemplates;
    private final List<FlattenedApplicationTemplateConfiguration> applicationTemplates;
    private final List<ParameterTemplateDescriptor> paramTemplates;
    private final List<VariableDescriptor> instanceVariables;

    private ProductManifest(String name, Manifest manifest, SortedSet<Manifest.Key> applications,
            SortedSet<Manifest.Key> references, ProductDescriptor desc, ObjectId cfgTreeId, List<ObjectId> plugins,
            List<FlattenedInstanceTemplateConfiguration> instanceTemplates,
            List<FlattenedApplicationTemplateConfiguration> applicationTemplates,
            List<ParameterTemplateDescriptor> paramTemplates, List<VariableDescriptor> instanceVariables) {
        this.prodName = name;
        this.manifest = manifest;
        this.applications = applications;
        this.references = references;
        this.desc = desc;
        this.cfgTreeId = cfgTreeId;
        this.plugins = plugins;
        this.instanceTemplates = instanceTemplates;
        this.applicationTemplates = applicationTemplates;
        this.paramTemplates = paramTemplates;
        this.instanceVariables = instanceVariables == null ? Collections.emptyList() : instanceVariables;
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
        try {
            ProductManifestStaticCacheRecordV2 cached = cacheStorage.read();

            if (cached != null) {
                return new ProductManifest(label, mf, cached.appRefs, cached.otherRefs, cached.desc, cached.cfgEntry,
                        cached.plugins, cached.templates, cached.applicationTemplates, cached.paramTemplates,
                        cached.instanceVariables);
            }
        } catch (Exception e) {
            // ignore, format changed...? will write updated version later.
            if (log.isDebugEnabled()) {
                log.debug("Failed to read cached product manifest", e);
            }
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
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.JAR_FILE_EXTENSION)) {
                    plugins.add(b.getElementId());
                }
            }).build());
        }

        List<VariableDescriptor> instanceVariables = new ArrayList<>();
        Tree.Key instanceVarsKey = new Tree.Key(ProductManifestBuilder.INSTANCE_VARIABLES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(instanceVarsKey)) {
            Set<VariableDescriptor> instanceVariableSet = new HashSet<>(); // temporary set for deduplication
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(instanceVarsKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.YAML_FILE_EXTENSION)) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        instanceVariableSet
                                .addAll(StorageHelper.fromYamlStream(is, InstanceVariableDefinitionDescriptor.class).definitions);
                    } catch (Exception e) {
                        log.warn("Cannot load instance variable definitions from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
            instanceVariables.addAll(instanceVariableSet);
        }

        List<InstanceTemplateDescriptor> templates = new ArrayList<>();
        Tree.Key templateKey = new Tree.Key(ProductManifestBuilder.TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(templateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(templateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.YAML_FILE_EXTENSION)) {
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
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.YAML_FILE_EXTENSION)) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        applicationTemplates.add(StorageHelper.fromYamlStream(is, ApplicationTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load application template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        List<ParameterTemplateDescriptor> paramTemplates = new ArrayList<>();
        Tree.Key paramTemplateKey = new Tree.Key(ProductManifestBuilder.PARAM_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(paramTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(paramTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.YAML_FILE_EXTENSION)) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        paramTemplates.add(StorageHelper.fromYamlStream(is, ParameterTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load application template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        List<InstanceVariableTemplateDescriptor> varTemplates = new ArrayList<>();
        Tree.Key varTemplateKey = new Tree.Key(ProductManifestBuilder.VARIABLE_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(varTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(varTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(ProductManifestBuilder.YAML_FILE_EXTENSION)) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        varTemplates.add(StorageHelper.fromYamlStream(is, InstanceVariableTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load instance variable template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        // lazy, DFS resolving of all templates.
        List<FlattenedApplicationTemplateConfiguration> resolvedAppTemplates = resolveApplicationTemplates(applicationTemplates);
        List<FlattenedInstanceTemplateConfiguration> resolvedInstanceTemplates = resolveInstanceTemplates(templates,
                applicationTemplates, varTemplates);

        // store persistent information.
        try {
            cacheStorage.store(appRefs, otherRefs, desc, cfgEntry, plugins, resolvedInstanceTemplates, resolvedAppTemplates,
                    paramTemplates, instanceVariables);
        } catch (Exception e) {
            // there is a chance for a race condition here, which actually does not do any harm (except for a
            // tiny performance hit since two threads calculate this). in case two threads try to persist the
            // exact same thing, we simply ignore the failure.
            if (log.isDebugEnabled()) {
                log.debug("Cannot store persistent cache for {}: {}", manifest, e.toString());
            }
        }

        return new ProductManifest(label, mf, appRefs, otherRefs, desc, cfgEntry, plugins, resolvedInstanceTemplates,
                resolvedAppTemplates, paramTemplates, instanceVariables);
    }

    private static List<FlattenedInstanceTemplateConfiguration> resolveInstanceTemplates(
            List<InstanceTemplateDescriptor> instTemplates, List<ApplicationTemplateDescriptor> applicationTemplates,
            List<InstanceVariableTemplateDescriptor> varTemplates) {
        return instTemplates.stream().map(t -> {
            try {
                return new FlattenedInstanceTemplateConfiguration(t, varTemplates, applicationTemplates);
            } catch (Exception e) {
                log.warn("Cannot resolve instance template {}: {}", t.name, e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception:", e);
                }
                return null;
            }
        }).filter(Objects::nonNull).sorted((a, b) -> a.name.compareToIgnoreCase(b.name)).toList();
    }

    private static List<FlattenedApplicationTemplateConfiguration> resolveApplicationTemplates(
            List<ApplicationTemplateDescriptor> applicationTemplates) {
        Comparator<String> nullSafeStringComp = Comparator.nullsFirst(String::compareToIgnoreCase);

        return applicationTemplates.stream().map(a -> {
            try {
                return new FlattenedApplicationTemplateConfiguration(a, applicationTemplates, null);
            } catch (Exception e) {
                log.warn("Cannot resolve standalone application template {}: {}", a.name, e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception:", e);
                }
                return null;
            }
        }).filter(Objects::nonNull).sorted((a, b) -> nullSafeStringComp.compare(a.name, b.name)).toList();
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
    public List<FlattenedInstanceTemplateConfiguration> getInstanceTemplates() {
        return instanceTemplates;
    }

    /**
     * @return a list of application templates which can be used when creating applications.
     */
    public List<FlattenedApplicationTemplateConfiguration> getApplicationTemplates() {
        return applicationTemplates;
    }

    /**
     * @return a list of templates which provide re-usable definitions of parameters.
     */
    public List<ParameterTemplateDescriptor> getParameterTemplates() {
        return paramTemplates;
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
     * @return the instance variables defined by the product
     */
    public List<VariableDescriptor> getInstanceVariables() {
        return instanceVariables;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link ProductManifest}s.
     * @return a {@link SortedSet} with all available {@link ProductManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive) {
        try {
            // product scanning and classification takes quite considerable time if done repeatedly. we cache the information
            // for a short period of time (a few minutes). Both adding (through spawn listener) and removing (through resources/UI/CLI)
            // will invalidate the cache earlier. Thus *normally* there should be no lag, but worst case an update would take
            // up to 5 minutes to be visible in the UI in case a product updates from a *very* unexpected direction.
            return SCAN_CACHE.get(hive, () -> {
                SortedSet<Manifest.Key> result = new TreeSet<>();

                // filter out internal (meta, etc.) manifests right away so we don't waste time checking.
                // this list is internally cached already, however this cache is not sufficient here...
                Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation()).stream()
                        .filter(k -> !k.getName().startsWith(".")).collect(Collectors.toSet());

                // the persistent classification already improves performance *a lot*. However also loading
                // this classification over and over again takes a lot of time. Thus this layer is cached once
                // more on layer up using soft references to further improve performance.
                PersistentManifestClassification<ProductClassification> pc = new PersistentManifestClassification<>(hive,
                        "products",
                        m -> new ProductClassification(m.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)));

                pc.loadAndUpdate(allKeys);
                pc.getClassifications().entrySet().stream().filter(e -> e.getValue().isProduct).map(Entry::getKey)
                        .forEach(result::add);

                return result;
            });
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot scan for products", e);
        }
    }

    /**
     * Force re-scanning for product manifests and updating of the persistent classification cache on next access.
     */
    public static void invalidateScanCache(BHive hive) {
        SCAN_CACHE.invalidate(hive);
    }

    public static final class ProductClassification {

        public final boolean isProduct;

        @JsonCreator
        public ProductClassification(@JsonProperty("isProduct") boolean prod) {
            isProduct = prod;
        }

    }

}
