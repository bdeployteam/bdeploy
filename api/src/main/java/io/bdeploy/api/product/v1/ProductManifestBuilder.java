package io.bdeploy.api.product.v1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;

import io.bdeploy.api.plugin.v1.Plugin;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ImportFileOperation;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Builder to create new product manifests.
 */
public class ProductManifestBuilder {

    public static final String PRODUCT_KEY_SUFFIX = "/product";

    public static final String PRODUCT_LABEL = "X-Product";
    public static final String PRODUCT_DESC = "product.json";
    public static final String CONFIG_ENTRY = "config";
    public static final String PLUGINS_ENTRY = "plugins";
    public static final String INSTANCE_VARIABLES_ENTRY = "instanceVariables";
    public static final String TEMPLATES_ENTRY = "templates";
    public static final String APP_TEMPLATES_ENTRY = "appTemplates";
    public static final String PARAM_TEMPLATES_ENTRY = "paramTemplates";
    public static final String VARIABLE_TEMPLATES_ENTRY = "variableTemplates";
    public static final String JAR_FILE_EXTENSION = ".jar";
    public static final String YAML_FILE_EXTENSION = ".yaml";

    private final Map<String, Manifest.Key> applications = new TreeMap<>();
    private final Map<String, String> labels = new TreeMap<>();
    private final ProductDescriptor desc;
    private Path configTemplates;
    private Path pluginFolder;
    private final List<Path> instanceVariables = new ArrayList<>();
    private final List<Path> instanceTemplates = new ArrayList<>();
    private final List<Path> appTemplates = new ArrayList<>();
    private final List<Path> paramTemplates = new ArrayList<>();
    private final List<Path> varTemplates = new ArrayList<>();

    public ProductManifestBuilder(ProductDescriptor desc) {
        this.desc = desc;
    }

    public synchronized ProductManifestBuilder add(Manifest.Key application) {
        applications.put(application.directoryFriendlyName(), application);
        return this;
    }

    public synchronized ProductManifestBuilder setConfigTemplates(Path templates) {
        configTemplates = templates;
        return this;
    }

    public synchronized ProductManifestBuilder setPluginFolder(Path plugins) {
        pluginFolder = plugins;
        return this;
    }

    public synchronized ProductManifestBuilder addLabel(String key, String value) {
        labels.put(key, value);
        return this;
    }

    public synchronized ProductManifestBuilder addInstanceVariables(Path definitionsPath) {
        instanceVariables.add(definitionsPath);
        return this;
    }

    public synchronized ProductManifestBuilder addInstanceTemplate(Path tmplPath) {
        instanceTemplates.add(tmplPath);
        return this;
    }

    public synchronized ProductManifestBuilder addApplicationTemplate(Path tmplPath) {
        appTemplates.add(tmplPath);
        return this;
    }

    public synchronized ProductManifestBuilder addParameterTemplate(Path tmplPath) {
        paramTemplates.add(tmplPath);
        return this;
    }

    public synchronized ProductManifestBuilder addInstanceVariableTemplate(Path tmplPath) {
        varTemplates.add(tmplPath);
        return this;
    }

    public synchronized void insert(BHive hive, Manifest.Key manifest, String productName) {
        try (Transaction t = hive.getTransactions().begin()) {
            doInsertLocked(hive, manifest, productName);
        }
    }

    private void doInsertLocked(BHive hive, Manifest.Key manifest, String productName) {
        Tree.Builder tree = new Tree.Builder();

        // add application references
        applications.forEach((k, v) -> tree.add(new Tree.Key(k, Tree.EntryType.MANIFEST),
                hive.execute(new InsertManifestRefOperation().setManifest(v))));

        // add product descriptor
        ObjectId descId = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(desc)));
        tree.add(new Tree.Key(PRODUCT_DESC, Tree.EntryType.BLOB), descId);

        // create config file tree
        if (configTemplates != null) {
            ObjectId configId = hive.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(configTemplates));
            tree.add(new Tree.Key(CONFIG_ENTRY, Tree.EntryType.TREE), configId);
        }

        // import product-bound plugins
        if (pluginFolder != null) {
            ObjectId pluginId = hive.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(pluginFolder));
            tree.add(new Tree.Key(PLUGINS_ENTRY, Tree.EntryType.TREE), pluginId);

            TreeView tv = hive.execute(new ScanOperation().setTree(pluginId));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(JAR_FILE_EXTENSION)) {
                    try (JarInputStream jis = new JarInputStream(
                            hive.execute(new ObjectLoadOperation().setObject(b.getElementId())))) {
                        java.util.jar.Manifest pluginMf = jis.getManifest();
                        if (pluginMf == null) {
                            throw new IllegalStateException("The plugin is not a valid JAR file: " + b.getName());
                        }

                        Attributes mainAttributes = pluginMf.getMainAttributes();
                        String mainClass = mainAttributes.getValue(Plugin.PLUGIN_CLASS_HEADER);
                        String name = mainAttributes.getValue(Plugin.PLUGIN_NAME_HEADER);

                        if (mainClass == null || name == null) {
                            throw new IllegalStateException("The plugin must define the '" + Plugin.PLUGIN_CLASS_HEADER
                                    + "' and '" + Plugin.PLUGIN_NAME_HEADER + "' headers: " + b.getName());
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException("The plugin cannot be read: " + b.getName(), e);
                    }
                }
            }).build());
        }

        // import instance variable definitions
        Tree.Builder instanceVarsTree = new Tree.Builder();
        for (Path p : instanceVariables) {
            ObjectId id = hive.execute(new ImportFileOperation().setFile(p));
            instanceVarsTree.add(new Tree.Key(id.toString() + YAML_FILE_EXTENSION, EntryType.BLOB), id);
        }
        tree.add(new Tree.Key(INSTANCE_VARIABLES_ENTRY, EntryType.TREE),
                hive.execute(new InsertArtificialTreeOperation().setTree(instanceVarsTree)));

        // import instance templates
        Tree.Builder templTree = new Tree.Builder();
        for (Path p : instanceTemplates) {
            ObjectId id = hive.execute(new ImportFileOperation().setFile(p));
            templTree.add(new Tree.Key(id.toString() + YAML_FILE_EXTENSION, EntryType.BLOB), id);
        }
        tree.add(new Tree.Key(TEMPLATES_ENTRY, EntryType.TREE),
                hive.execute(new InsertArtificialTreeOperation().setTree(templTree)));

        // import application templates
        Tree.Builder appTemplTree = new Tree.Builder();
        for (Path p : appTemplates) {
            ObjectId id = hive.execute(new ImportFileOperation().setFile(p));
            appTemplTree.add(new Tree.Key(id.toString() + YAML_FILE_EXTENSION, EntryType.BLOB), id);
        }
        tree.add(new Tree.Key(APP_TEMPLATES_ENTRY, EntryType.TREE),
                hive.execute(new InsertArtificialTreeOperation().setTree(appTemplTree)));

        // import parameter templates
        Tree.Builder paramTemplTree = new Tree.Builder();
        for (Path p : paramTemplates) {
            ObjectId id = hive.execute(new ImportFileOperation().setFile(p));
            paramTemplTree.add(new Tree.Key(id.toString() + YAML_FILE_EXTENSION, EntryType.BLOB), id);
        }
        tree.add(new Tree.Key(PARAM_TEMPLATES_ENTRY, EntryType.TREE),
                hive.execute(new InsertArtificialTreeOperation().setTree(paramTemplTree)));

        // import variable templates
        Tree.Builder varTemplTree = new Tree.Builder();
        for (Path p : varTemplates) {
            ObjectId id = hive.execute(new ImportFileOperation().setFile(p));
            varTemplTree.add(new Tree.Key(id.toString() + YAML_FILE_EXTENSION, EntryType.BLOB), id);
        }
        tree.add(new Tree.Key(VARIABLE_TEMPLATES_ENTRY, EntryType.TREE),
                hive.execute(new InsertArtificialTreeOperation().setTree(varTemplTree)));

        Manifest.Builder m = new Manifest.Builder(manifest);
        labels.forEach(m::addLabel);
        m.addLabel(PRODUCT_LABEL, productName).setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tree)));
        hive.execute(new InsertManifestOperation().addManifest(m.build(hive)));
    }

    /**
     * @param descriptorPath a relative or absolute path to a directory or a product-info.yaml file.
     * @param hive the target hive to import to.
     * @param fetcher a {@link DependencyFetcher} capable of assuring that external dependencies are present.
     * @param parallel whether it is allowed to spawn threads, or the import must happen on the calling thread.
     */
    public static Manifest.Key importFromDescriptor(Path descriptorPath, BHive hive, DependencyFetcher fetcher,
            boolean parallel) {
        // 1. read product desc yaml.
        descriptorPath = getDescriptorPath(descriptorPath);
        descriptorPath = descriptorPath.toAbsolutePath();
        ProductDescriptor prod = readProductDescriptor(descriptorPath);

        // 2. read version descriptor.
        if (prod.versionFile == null || prod.versionFile.isEmpty()) {
            throw new IllegalStateException(
                    "product descriptor does not reference a product version descriptor, which is required.");
        }
        // path is relative to product descriptor, or absolute.
        Path vDesc = descriptorPath.getParent().resolve(prod.versionFile);
        ProductVersionDescriptor versions = readProductVersionDescriptor(descriptorPath, vDesc);

        // 3. validate product and version info.
        RuntimeAssert.assertNotNull(versions.version, "no version defined in " + vDesc);
        RuntimeAssert.assertNotNull(prod.name, "no name defined in " + descriptorPath);
        RuntimeAssert.assertNotNull(prod.product, "no name defined in " + descriptorPath);

        // make sure that all applications are there in the version descriptor.
        Map<String, Map<OperatingSystem, String>> toImport = new TreeMap<>();
        matchApplicationsWithDescriptor(prod, vDesc, versions, toImport);

        // 4. prepare product meta-data and builder to be filled.
        Manifest.Key prodKey = new Manifest.Key(prod.product + PRODUCT_KEY_SUFFIX, versions.version);

        // 4a. check if product is already present
        if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(prodKey)))) {
            throw new IllegalStateException("Product " + prodKey + " is already present.");
        }

        // 5. find and import all applications to import.
        ProductManifestBuilder builder = new ProductManifestBuilder(prod);
        Path impBasePath = descriptorPath.getParent();
        importApplications(hive, fetcher, versions, toImport, prod.product + '/', builder, impBasePath, parallel);

        // 6. additional labels
        versions.labels.forEach(builder::addLabel);

        // 7. configuration templates
        if (prod.configTemplates != null) {
            Path cfgDir = descriptorPath.getParent().resolve(prod.configTemplates);
            if (!Files.isDirectory(cfgDir)) {
                throw new IllegalStateException("Configuration template directory not found: " + cfgDir);
            }
            builder.setConfigTemplates(cfgDir);
        }

        // 8. product-bound plugins
        if (prod.pluginFolder != null) {
            Path pluginPath = descriptorPath.getParent().resolve(prod.pluginFolder);
            if (!Files.isDirectory(pluginPath)) {
                throw new IllegalStateException("Plugin directory not found: " + pluginPath);
            }
            builder.setPluginFolder(pluginPath);
        }

        // 9. instance variable definitions
        if (prod.instanceVariableDefinitions != null && !prod.instanceVariableDefinitions.isEmpty()) {
            for (String definitions : prod.instanceVariableDefinitions) {
                Path definitionsPath = descriptorPath.getParent().resolve(definitions);
                if (!Files.isRegularFile(definitionsPath)) {
                    throw new IllegalStateException("Instance Variable Definition descriptor not found: " + definitionsPath);
                }
                builder.addInstanceVariables(definitionsPath);
            }
        }

        // 10. instance templates
        if (prod.instanceTemplates != null && !prod.instanceTemplates.isEmpty()) {
            for (String tmpl : prod.instanceTemplates) {
                Path tmplPath = descriptorPath.getParent().resolve(tmpl);
                if (!Files.isRegularFile(tmplPath)) {
                    throw new IllegalStateException("Instance Template descriptor not found: " + tmplPath);
                }
                builder.addInstanceTemplate(tmplPath);
            }
        }

        // 11. application templates
        if (prod.applicationTemplates != null && !prod.applicationTemplates.isEmpty()) {
            for (String tmpl : prod.applicationTemplates) {
                Path tmplPath = descriptorPath.getParent().resolve(tmpl);
                if (!Files.isRegularFile(tmplPath)) {
                    throw new IllegalStateException("Application Template descriptor not found: " + tmplPath);
                }
                builder.addApplicationTemplate(tmplPath);
            }
        }

        // 12. parameter templates
        if (prod.parameterTemplates != null && !prod.parameterTemplates.isEmpty()) {
            for (String tmpl : prod.parameterTemplates) {
                Path tmplPath = descriptorPath.getParent().resolve(tmpl);
                if (!Files.isRegularFile(tmplPath)) {
                    throw new IllegalStateException("Parameter Template descriptor not found: " + tmplPath);
                }
                builder.addParameterTemplate(tmplPath);
            }
        }

        // 13. instance variable templates
        if (prod.instanceVariableTemplates != null && !prod.instanceVariableTemplates.isEmpty()) {
            for (String tmpl : prod.instanceVariableTemplates) {
                Path tmplPath = descriptorPath.getParent().resolve(tmpl);
                if (!Files.isRegularFile(tmplPath)) {
                    throw new IllegalStateException("Parameter Template descriptor not found: " + tmplPath);
                }
                builder.addInstanceVariableTemplate(tmplPath);
            }
        }

        // 14. generate product
        builder.insert(hive, prodKey, prod.product);

        return prodKey;
    }

    public static Path getDescriptorPath(Path descriptorPath) {
        if (Files.isDirectory(descriptorPath)) {
            descriptorPath = descriptorPath.resolve("product-info.yaml");
        }
        return descriptorPath;
    }

    private static void matchApplicationsWithDescriptor(ProductDescriptor prod, Path vDesc, ProductVersionDescriptor versions,
            Map<String, Map<OperatingSystem, String>> toImport) {
        for (String appName : prod.applications) {
            Map<OperatingSystem, String> map = versions.appInfo.get(appName);
            if (map == null || map.isEmpty()) {
                throw new IllegalStateException("Cannot find build information for " + appName + " in " + vDesc);
            }

            // sanity check - the product manifest will be called 'product'
            RuntimeAssert.assertFalse("product".equals(appName), "application may not be named 'product'");

            toImport.put(appName, map);
        }
    }

    private static void importApplications(BHive hive, DependencyFetcher fetcher, ProductVersionDescriptor versions,
            Map<String, Map<OperatingSystem, String>> toImport, String baseName, ProductManifestBuilder builder, Path impBasePath,
            boolean parallel) {
        List<Callable<ApplicationDescriptorApi>> tasks = doGatherImportTasks(hive, fetcher, versions, toImport, baseName, builder,
                impBasePath);

        try {
            if (parallel) {
                for (Future<ApplicationDescriptorApi> f : ForkJoinPool.commonPool().invokeAll(tasks)) {
                    f.get();
                }
            } else {
                for (Callable<ApplicationDescriptorApi> c : tasks) {
                    c.call();
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to import", ie);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import", e);
        }
    }

    /**
     * Find all required tasks for the import
     */
    private static List<Callable<ApplicationDescriptorApi>> doGatherImportTasks(BHive hive, DependencyFetcher fetcher,
            ProductVersionDescriptor versions, Map<String, Map<OperatingSystem, String>> toImport, String baseName,
            ProductManifestBuilder builder, Path impBasePath) {
        List<Callable<ApplicationDescriptorApi>> tasks = new ArrayList<>();
        for (Map.Entry<String, Map<OperatingSystem, String>> entry : toImport.entrySet()) {
            for (Map.Entry<OperatingSystem, String> relApp : entry.getValue().entrySet()) {
                Path appPath = impBasePath.resolve(relApp.getValue());
                if (Files.isDirectory(appPath)) {
                    appPath = appPath.resolve(ApplicationDescriptorApi.FILE_NAME);
                }

                if (!PathHelper.exists(appPath)) {
                    throw new IllegalStateException("Cannot find " + appPath + " while importing " + entry.getKey());
                }

                Path finalAppPath = appPath;

                tasks.add(() -> importDependenciesAndApplication(hive, fetcher, versions, baseName, builder, entry.getKey(),
                        relApp.getKey(), finalAppPath));
            }
        }
        return tasks;
    }

    private static ApplicationDescriptorApi importDependenciesAndApplication(BHive hive, DependencyFetcher fetcher,
            ProductVersionDescriptor versions, String baseName, ProductManifestBuilder builder, String appName,
            OperatingSystem os, Path appPath) {
        // read and resolve dependencies.
        ApplicationDescriptorApi appDesc;
        try (InputStream is = Files.newInputStream(appPath)) {
            appDesc = StorageHelper.fromYamlStream(is, ApplicationDescriptorApi.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + appPath, e);
        }

        RuntimeAssert.assertTrue(appDesc.supportedOperatingSystems.contains(os),
                "Application " + appName + " does not support operating system " + os);

        fetcher.fetch(hive, appDesc.runtimeDependencies, os).forEach(builder::add);

        try (Transaction t = hive.getTransactions().begin()) {
            builder.add(hive.execute(new ImportOperation().setSourcePath(appPath.getParent())
                    .setManifest(new ScopedManifestKey(baseName + appName, os, versions.version).getKey())));
        }

        return appDesc;
    }

    public static ProductVersionDescriptor readProductVersionDescriptor(Path impDesc, Path vDesc) {
        if (!PathHelper.exists(vDesc)) {
            throw new IllegalStateException("Cannot find version descriptor at " + vDesc);
        }
        ProductVersionDescriptor versions;
        try (InputStream is = Files.newInputStream(vDesc)) {
            versions = StorageHelper.fromYamlStream(is, ProductVersionDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + impDesc, e);
        }
        return versions;
    }

    public static ProductDescriptor readProductDescriptor(Path impDesc) {
        if (!PathHelper.exists(impDesc)) {
            throw new IllegalArgumentException("Product descriptor does not exist: " + impDesc);
        }
        ProductDescriptor prod;
        try (InputStream is = Files.newInputStream(impDesc)) {
            prod = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + impDesc, e);
        }
        return prod;
    }
}
