package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Base64;
import org.apache.poi.ss.formula.eval.NotImplementedException;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.product.v1.impl.MultiLocalDependencyFetcher;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateInstanceTemplateGroupMapping;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductBulkResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.InstanceUsageDto;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

public class ProductResourceImpl implements ProductResource {

    private static final String RELPATH_ERROR = "Only relative paths within the ZIP file are allowed, '..' is forbidden. Offending path: %1$s";

    @Inject
    private BHiveRegistry registry;

    @Context
    private ResourceContext rc;

    @Inject
    private Minion minion;

    @Inject
    private PluginManager pm;

    @Inject
    private VersionSorterService vss;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private ActionFactory af;

    private final BHive hive;

    private final String group;

    public ProductResourceImpl(BHive hive, String group) {
        this.hive = hive;
        this.group = group;
    }

    @Override
    public List<ProductDto> list(String name) {
        Predicate<ProductManifest> filter = p -> true;
        if (name != null && !name.isBlank()) {
            filter = p -> p.getKey().getName().equals(name);
        }

        SortedSet<Key> scan = ProductManifest.scan(hive);
        List<ProductDto> result = scan.stream().map(this::getProductManifest).filter(Objects::nonNull).filter(filter)
                .map(ProductDto::create).collect(Collectors.toList());
        if (!result.isEmpty()) {
            Map<String, Comparator<Manifest.Key>> comparators = new TreeMap<>();
            result.sort((a, b) -> {
                Comparator<Manifest.Key> productVersionComparator = comparators.computeIfAbsent(a.key.getName(),
                        k -> vss.getKeyComparator(group, a.key));

                return productVersionComparator.compare(a.key, b.key);
            });
        }

        return result;
    }

    private ProductManifest getProductManifest(Key k) {
        try {
            return ProductManifest.of(hive, k);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void delete(String name, String tag) {
        try (ActionHandle h = af.run(Actions.DELETE_PRODUCT, group, null, name + ":" + tag)) {
            Manifest.Key key = new Manifest.Key(name, tag);
            Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
            if (existing.size() != 1) {
                throw new WebApplicationException("Cannot identify " + key + " to delete", Status.BAD_REQUEST);
            }

            if (!getProductUsedIn(name, tag).isEmpty()) {
                throw new WebApplicationException("Product version is still in use", Status.BAD_REQUEST);
            }

            // unload any plugins loaded from this version
            pm.unloadProduct(key);

            // This assumes that no single application version is used in multiple products.
            ProductManifest pmf = ProductManifest.of(hive, key);
            SortedSet<Key> apps = pmf.getApplications();

            hive.execute(new ManifestDeleteOperation().setToDelete(key));
            apps.forEach(a -> hive.execute(new ManifestDeleteOperation().setToDelete(a)));

            ProductManifest.invalidateScanCache(hive);
            changes.remove(ObjectChangeType.PRODUCT, key);
        }
    }

    @Override
    public ApplicationResource getApplicationResource(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        return rc.initResource(new ApplicationResourceImpl(hive, key));
    }

    @Override
    public List<InstanceUsageDto> getProductUsedIn(String name, String tag) {
        Manifest.Key checkKey = new Manifest.Key(name, tag);
        return internalCheckUsedIn(hive, checkKey);
    }

    /**
     * Check if a product is still in use by any instance, and if yes, returnes a list of usage informations.
     */
    static List<InstanceUsageDto> internalCheckUsedIn(BHive hive, Manifest.Key checkKey) {
        // InstanceManifests using the product version grouped by instance
        Map<String, Set<InstanceManifest>> id2imSet = InstanceManifest.scan(hive, false).stream()
                .map(k -> InstanceManifest.of(hive, k)).filter(im -> im.getConfiguration().product.equals(checkKey))
                .collect(Collectors.groupingBy(im -> im.getConfiguration().id, Collectors.toSet()));

        List<InstanceUsageDto> result = new ArrayList<>();

        for (Set<InstanceManifest> mfSet : id2imSet.values()) {
            // grouped by ID so we need to read the installed state only once per instance.
            Set<String> installedTags = mfSet.stream().findFirst().get().getState(hive).read().installedTags;

            if (installedTags.isEmpty()) {
                // in this case, we take the latest, and need to still block this product version from removal.
                // (instance was created, but never installed (yet)).
                InstanceManifest newest = mfSet.stream().sorted(
                        (a, b) -> Integer.compare(Integer.parseInt(b.getKey().getTag()), Integer.parseInt(a.getKey().getTag())))
                        .findFirst().orElseThrow();

                result.add(createUsage(newest));
            } else {
                mfSet.stream().filter(mf -> installedTags.contains(mf.getKey().getTag()))
                        .sorted((a, b) -> Long.compare(Long.parseLong(a.getKey().getTag()), Long.parseLong(b.getKey().getTag())))
                        .forEach(mf -> result.add(createUsage(mf)));
            }
        }
        return result;
    }

    private static InstanceUsageDto createUsage(InstanceManifest mf) {
        InstanceUsageDto dto = new InstanceUsageDto();
        dto.id = mf.getConfiguration().id;
        dto.name = mf.getConfiguration().name;
        dto.description = mf.getConfiguration().description;
        dto.tag = mf.getKey().getTag();
        return dto;
    }

    @Override
    public String createProductZipFile(String name, String tag) {
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        try (ActionHandle h = af.run(Actions.DOWNLOAD_PRODUCT_H, group, null, name + ":" + tag)) {
            return ds.createManifestZipAndRegister(hive, name, tag);
        }
    }

    @Override
    public List<Manifest.Key> upload(FormDataMultiPart fdmp) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        try {
            // Download the hive to a temporary location
            Files.copy(FormDataHelper.getStreamFromMultiPart(fdmp), targetFile);

            // check if the uploaded file is a hive or "something else".
            boolean isHive = false;
            boolean hasProductInfo = false;
            try (FileSystem zfs = PathHelper.openZip(targetFile)) {
                if (Files.exists(zfs.getPath("manifests")) && Files.exists(zfs.getPath("objects"))) {
                    isHive = true;
                }

                if (Files.exists(zfs.getPath("product-info.yaml"))) {
                    hasProductInfo = true;
                }
            }

            List<Key> result;
            if (isHive) {
                result = importFromUploadedBHive(targetFile);
            } else if (hasProductInfo) {
                result = importFromUploadedProductInfo(targetFile);
            } else {
                throw new WebApplicationException("Uploaded ZIP is neither a BHive, nor has a product-info.yaml",
                        Status.BAD_REQUEST);
            }

            return result;
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), e, Status.BAD_REQUEST);
        } finally {
            PathHelper.deleteRecursiveRetry(targetFile);
        }
    }

    /**
     * Import a product from a ZIP file which contains the product definition as well as all applications.
     * <p>
     * Limitations:
     * <ul>
     * <li>All paths within product-info.yaml and product-version.yaml <b>MUST</b> be relative and may not contain '..'.
     * <li>All external dependencies must already exist in the target instance group or in local software repository.
     * </ul>
     *
     * @param targetFile the ZIP file uploaded by the user.
     * @return the {@link Key} imported in a {@link Collections#singletonList(Object) singleton list}.
     * @throws IOException in case of an I/O error
     */
    private List<Manifest.Key> importFromUploadedProductInfo(Path targetFile) throws IOException {
        try (FileSystem zfs = PathHelper.openZip(targetFile)) {
            DependencyFetcher fetcher = new MultiLocalDependencyFetcher(this.getLocalSoftwareRepositories());

            // validate paths, etc. neither product-info.yaml, nor product-version.yaml are allowed to use '..' in paths.
            Path desc = ProductManifestBuilder.getDescriptorPath(zfs.getPath("/"));
            ProductDescriptor pd = ProductManifestBuilder.readProductDescriptor(desc);

            assertNullOrRelativePath(pd.configTemplates);
            assertNullOrRelativePath(pd.versionFile);
            pd.instanceTemplates.forEach(ProductResourceImpl::assertNullOrRelativePath);
            pd.applicationTemplates.forEach(ProductResourceImpl::assertNullOrRelativePath);
            assertNullOrRelativePath(pd.pluginFolder);

            Path vDesc = desc.getParent().resolve(pd.versionFile);
            ProductVersionDescriptor pvd = ProductManifestBuilder.readProductVersionDescriptor(desc, vDesc);

            for (Entry<String, Map<OperatingSystem, String>> entry : pvd.appInfo.entrySet()) {
                for (Entry<OperatingSystem, String> loc : entry.getValue().entrySet()) {
                    RuntimeAssert.assertFalse(loc.getValue().contains("..") || loc.getValue().startsWith("/"),
                            String.format(RELPATH_ERROR, loc.getValue()));
                }
            }

            return Collections.singletonList(ProductManifestBuilder.importFromDescriptor(desc, hive, fetcher, false));
        }
    }

    private List<BHive> getLocalSoftwareRepositories() {
        List<BHive> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    private static void assertNullOrRelativePath(String p) {
        if (p != null) {
            RuntimeAssert.assertFalse(p.contains("..") || p.startsWith("/"), String.format(RELPATH_ERROR, p));
        }
    }

    private List<Manifest.Key> importFromUploadedBHive(Path targetFile) {
        List<Manifest.Key> imported = new ArrayList<>();

        // Read all product manifests
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();

        try (BHive zipHive = new BHive(targetUri, null, new ActivityReporter.Null())) {
            SortedSet<Key> productKeys = ProductManifest.scan(zipHive);
            if (productKeys.isEmpty()) {
                throw new WebApplicationException("ZIP file does not contain a product.", Status.BAD_REQUEST);
            }

            // Determine required objects
            CopyOperation copy = new CopyOperation().setDestinationHive(hive);
            ObjectListOperation scan = new ObjectListOperation();
            for (Key productKey : productKeys) {
                // Ignore existing products
                if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(productKey)))) {
                    continue;
                }
                imported.add(productKey);
                copy.addManifest(productKey);
                scan.addManifest(productKey);
            }

            if (imported.isEmpty()) {
                throw new WebApplicationException("All contained products are already present in the target.",
                        Status.BAD_REQUEST);
            }

            // Add all required artifacts
            Set<ObjectId> objectIds = zipHive.execute(scan);
            objectIds.forEach(copy::addObject);

            // Execute import only if we have something to do
            if (!imported.isEmpty()) {
                zipHive.execute(copy);
            }
            return imported;
        }
    }

    @Override
    public void copyProduct(String softwareRepository, String productName, List<String> productTags) {
        try (ActionHandle h = af.runMulti(Actions.IMPORT_PROD_REPO, group, null,
                productTags.stream().map(t -> productName + ":" + t).toList())) {
            BHive repoHive = registry.get(softwareRepository);
            if (repoHive == null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            for (String productTag : productTags) {
                Manifest.Key key = new Manifest.Key(productName, productTag);

                Set<ObjectId> objectIds = repoHive.execute(new ObjectListOperation().addManifest(key));
                CopyOperation copy = new CopyOperation().setDestinationHive(hive).addManifest(key);
                objectIds.forEach(copy::addObject);
                repoHive.execute(copy);
            }

            InstanceGroupResource igr = rc.getResource(InstanceGroupResourceImpl.class);
            InstanceGroupConfiguration igc = igr.getInstanceGroupConfigurationDto(this.group).instanceGroupConfiguration;
            if (igc.productToRepo == null) {
                igc.productToRepo = new HashMap<>();
            }
            igc.productToRepo.put(productName, softwareRepository);
            igr.update(this.group, igc);

            ProductManifest.invalidateScanCache(hive);
        }
    }

    @Override
    public String loadConfigFile(String name, String tag, String file) {
        Manifest.Key key = new Manifest.Key(name, tag);
        ProductManifest productManifest = ProductManifest.of(hive, key);
        ObjectId cfgTree = productManifest.getConfigTemplateTreeId();

        if (cfgTree == null) {
            throw new WebApplicationException("Cannot find file: " + file, Status.NOT_FOUND);
        }

        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(cfgTree).setRelativePath(file));
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return Base64.encodeBase64String(baos.toByteArray());
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read configuration file: " + file, e);
        }
    }

    @Override
    public String getResponseFile(String productId, String version, String instanceTemplate, Boolean includeDefaults) {
        List<ProductDto> versions = list(productId + ProductManifestBuilder.PRODUCT_KEY_SUFFIX);
        if (versions.isEmpty()) {
            throw new WebApplicationException("Product with ID " + productId + " could not be found");
        }

        boolean versionSet = version != null;
        ProductDto selectedVersion = null;
        if (versionSet) {
            for (ProductDto productDto : versions) {
                if (productDto.key.getTag().equals(version)) {
                    selectedVersion = productDto;
                    break;
                }
            }
            if (selectedVersion == null) {
                throw new WebApplicationException("Version " + version + " of product " + productId + " could not be found");
            }
        } else {
            selectedVersion = versions.iterator().next();
            version = selectedVersion.key.getTag();
        }

        List<FlattenedInstanceTemplateConfiguration> instanceTemplates = selectedVersion.instanceTemplates;
        FlattenedInstanceTemplateConfiguration selectedInstanceTemplate;
        switch (instanceTemplates.size()) {
            case 0:
                throw new WebApplicationException(
                        "Version " + version + " of product " + productId + " does not contain an instance template");
            case 1:
                selectedInstanceTemplate = instanceTemplates.iterator().next();
                break;
            default:
                if (instanceTemplate == null) {
                    throw new WebApplicationException("Version " + version + " of product " + productId
                            + " contains multiple instance templates. Please specify one using the instanceTemplate parameter");
                }
                Optional<FlattenedInstanceTemplateConfiguration> selectedInstanceTemplateOpt = instanceTemplates.stream()
                        .filter(t -> t.name.equals(instanceTemplate)).findFirst();
                if (selectedInstanceTemplateOpt.isEmpty()) {
                    throw new WebApplicationException("Version " + version + " of product " + productId
                            + " does not contain an instance template called " + instanceTemplate);
                }
                selectedInstanceTemplate = selectedInstanceTemplateOpt.get();
                break;
        }

        Stream<TemplateVariable> tempVarStream = Stream.concat(selectedInstanceTemplate.directlyUsedTemplateVars.stream(),
                selectedInstanceTemplate.groups.stream().flatMap(g -> g.groupVariables.stream()));
        Set<TemplateVariableFixedValueOverride> tempVars;
        if (includeDefaults != null && includeDefaults) {
            tempVars = tempVarStream//
                    .sorted((a, b) -> {
                        boolean aHasDefault = a.defaultValue != null;
                        boolean bHasDefault = b.defaultValue != null;
                        if (aHasDefault == bHasDefault) {
                            return 0;
                        }
                        return aHasDefault ? 1 : -1;
                    })//
                    .map(var -> new TemplateVariableFixedValueOverride(var.id,
                            var.defaultValue != null ? var.defaultValue : "<" + var.type + " VALUE>"))
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            tempVars = tempVarStream//
                    .filter(var -> var.defaultValue == null)
                    .map(var -> new TemplateVariableFixedValueOverride(var.id, "<" + var.type + " VALUE>"))//
                    .collect(Collectors.toUnmodifiableSet());
        }

        InstanceTemplateReferenceDescriptor dataHolder = new InstanceTemplateReferenceDescriptor();
        dataHolder.name = "Instance of " + selectedVersion.name;
        dataHolder.description = "This is an instance containing " + selectedVersion.name + (versionSet ? ":" + version : "");
        dataHolder.productId = productId;
        dataHolder.productVersionRegex = "<OPTIONAL REGEX EXPRESSION>";
        dataHolder.initialProductVersionRegex = versionSet ? version : "<OPTIONAL REGEX EXPRESSION>";
        dataHolder.templateName = selectedInstanceTemplate.name;
        dataHolder.defaultMappings = selectedInstanceTemplate.groups.stream().map(x -> {
            String node = switch (x.type) {
                case null -> "master";
                case SERVER -> "master";
                case CLIENT -> InstanceManifest.CLIENT_NODE_LABEL;
                default -> throw new NotImplementedException("Unknown instance template group type: " + x.type);
            };
            var mapping = new SystemTemplateInstanceTemplateGroupMapping();
            mapping.group = x.name;
            mapping.node = node;
            return mapping;
        }).toList();
        dataHolder.fixedVariables = new ArrayList<>(tempVars);

        ObjectMapper mapper = JacksonHelper.getDefaultYamlObjectMapper();
        String yamlOutput;
        try {
            yamlOutput = mapper.writeValueAsString(dataHolder);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return yamlOutput;
    }

    @Override
    public ProductBulkResource getBulkResource() {
        return rc.initResource(new ProductBulkResourceImpl(hive, group));
    }
}
