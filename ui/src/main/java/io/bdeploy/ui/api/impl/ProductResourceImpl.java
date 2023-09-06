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
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.Minion;
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

    private static final Logger log = LoggerFactory.getLogger(ProductResourceImpl.class);

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
        List<ProductDto> result = new ArrayList<>();
        SortedSet<Key> scan = ProductManifest.scan(hive);

        Predicate<ProductManifest> filter = p -> true;
        if (name != null && !name.isBlank()) {
            filter = p -> p.getKey().getName().equals(name);
        }

        scan.stream().map(k -> ProductManifest.of(hive, k)).filter(filter).forEach(p -> result.add(ProductDto.create(p)));
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

    @Override
    public void delete(String name, String tag) {
        try (ActionHandle h = af.run(Actions.DELETE_PRODUCT, group, null, name + ":" + tag)) {
            Manifest.Key key = new Manifest.Key(name, tag);
            Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
            if (existing.size() != 1) {
                log.warn("Cannot uniquely identify {} to delete", key);
                return;
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

        // InstanceManifests using the product version grouped by instance
        Map<String, Set<InstanceManifest>> id2imSet = InstanceManifest.scan(hive, false).stream()
                .map(k -> InstanceManifest.of(hive, k)).filter(im -> im.getConfiguration().product.equals(checkKey))
                .collect(Collectors.groupingBy(im -> im.getConfiguration().id, Collectors.toSet()));

        List<InstanceUsageDto> result = new ArrayList<>();

        for (Set<InstanceManifest> mfSet : id2imSet.values()) {
            // grouped by ID so we need to read the installed state only once per instance.
            Set<String> installedTags = mfSet.stream().findFirst().get().getState(hive).read().installedTags;

            mfSet.stream().filter(mf -> installedTags.contains(mf.getManifest().getTag())).sorted(
                    (a, b) -> Long.compare(Long.parseLong(a.getManifest().getTag()), Long.parseLong(b.getManifest().getTag())))
                    .forEach(mf -> {
                        InstanceUsageDto dto = new InstanceUsageDto();
                        dto.id = mf.getConfiguration().id;
                        dto.name = mf.getConfiguration().name;
                        dto.description = mf.getConfiguration().description;
                        dto.tag = mf.getManifest().getTag();
                        result.add(dto);
                    });
        }

        return result;
    }

    @Override
    public String createProductZipFile(String name, String tag, boolean original) {
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        if (original) {
            try (ActionHandle h = af.run(Actions.DOWNLOAD_PRODUCT_C, group, null, name + ":" + tag)) {
                return ds.createOriginalZipAndRegister(hive, name, tag);
            }
        } else {
            try (ActionHandle h = af.run(Actions.DOWNLOAD_PRODUCT_H, group, null, name + ":" + tag)) {
                return ds.createManifestZipAndRegister(hive, name, tag);
            }
        }
    }

    @Override
    public List<Manifest.Key> upload(InputStream inputStream) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        try {
            // Download the hive to a temporary location
            Files.copy(inputStream, targetFile);

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
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
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
            pd.instanceTemplates.forEach(this::assertNullOrRelativePath);
            pd.applicationTemplates.forEach(this::assertNullOrRelativePath);
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

    private void assertNullOrRelativePath(String p) {
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
}
