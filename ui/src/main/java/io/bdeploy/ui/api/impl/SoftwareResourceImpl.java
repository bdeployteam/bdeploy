package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareResource;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.UploadInfoDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

public class SoftwareResourceImpl implements SoftwareResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareResourceImpl.class);
    private static final String RELPATH_ERROR = "Only relative paths within the ZIP file are allowed, '..' is forbidden. Offending path: %1$s";

    @Context
    private ResourceContext rc;

    @Inject
    private Minion minion;

    @Inject
    private VersionSorterService vss;

    @Inject
    private ChangeEventManager changes;

    private final BHive hive;

    private final String repository;

    public SoftwareResourceImpl(BHive hive, String repository) {
        this.hive = hive;
        this.repository = repository;
    }

    @Override
    public List<Manifest.Key> list(boolean products, boolean generic) {
        List<Manifest.Key> result = new ArrayList<>();
        Set<Manifest.Key> apps = new HashSet<>();

        Set<Key> keySet = hive.execute(new ManifestListOperation());
        for (Manifest.Key k : keySet) {
            if (SoftwareRepositoryManifest.isSoftwareRepositoryManifest(k) || k.getName().startsWith(".")) {
                continue;
            }
            // collect all non-products and all manifests that belong to products
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(k).setNullOnError(true));
            if (mf != null) {
                if (mf.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)) {
                    ProductManifest pmf = ProductManifest.of(hive, k);
                    apps.addAll(pmf.getApplications());
                } else {
                    result.add(k);
                }
            }
        }
        result.removeIf(apps::contains); // remove all manifests that belong to a product
        result.sort(vss.getKeyComparator(null, null));
        return result;
    }

    @Override
    public void delete(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
        if (existing.size() != 1) {
            log.warn("Cannot uniquely identify {} to delete", key);
            return;
        }

        hive.execute(new ManifestDeleteOperation().setToDelete(key));

        changes.change(ObjectChangeType.SOFTWARE_PACKAGE, key);
    }

    @Override
    public String createSoftwareZipFile(String name, String tag, boolean original) {
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        if (original) {
            return ds.createOriginalZipAndRegister(hive, name, tag);
        } else {
            return ds.createManifestZipAndRegister(hive, name, tag);
        }
    }

    @Override
    public List<Manifest.Key> upload(InputStream inputStream) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        List<Manifest.Key> imported = new ArrayList<>();

        // Download the hive to a temporary location
        try {
            Files.copy(inputStream, targetFile);
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
        }

        // Read all product manifests
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
        try (BHive zipHive = new BHive(targetUri, null, new ActivityReporter.Null())) {
            Set<Key> manifestKeys = zipHive.execute(new ManifestListOperation());
            if (manifestKeys.isEmpty()) {
                throw new WebApplicationException("ZIP file does not contain a manifest.", Status.BAD_REQUEST);
            }

            // Determine required objects
            CopyOperation copy = new CopyOperation().setDestinationHive(hive);
            ObjectListOperation scan = new ObjectListOperation();
            for (Key manifestKey : manifestKeys) {
                // Ignore existing software
                if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(manifestKey)))) {
                    continue;
                }

                imported.add(manifestKey);

                copy.addManifest(manifestKey);
                scan.addManifest(manifestKey);
            }

            // Add all required artifacts
            Set<ObjectId> objectIds = zipHive.execute(scan);
            objectIds.forEach(copy::addObject);

            // Execute import only if we have something to do
            if (!imported.isEmpty()) {
                zipHive.execute(copy);
            }
            return imported;
        } finally {
            PathHelper.deleteRecursiveRetry(targetFile);
        }
    }

    @Override
    public UploadInfoDto uploadRawContent(InputStream inputStream, String manifestName, String manifestTag, String supportedOS) {
        UploadInfoDto dto = new UploadInfoDto();

        dto.tmpFilename = UuidHelper.randomId() + ".zip";
        dto.isHive = false;
        dto.isProduct = false;
        Path targetFile = minion.getDownloadDir().resolve(dto.tmpFilename);
        try {
            Files.copy(inputStream, targetFile);
            URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
            try (FileSystem fs = FileSystems.newFileSystem(targetUri, new TreeMap<>())) {
                if (Files.exists(fs.getPath("/manifests")) && Files.exists(fs.getPath("/objects"))) { // is hive?
                    dto.isHive = true;
                } else if (Files.exists(fs.getPath("/product-info.yaml"))) { // is product-zip?
                    dto.isProduct = true;
                    dto.details = "Zip file containing a product";
                } else { // generic zip file
                    dto.details = "Generic zip file";
                }
            }

            if (dto.isHive) {
                try (BHive zipHive = new BHive(targetUri, null, new ActivityReporter.Null())) {
                    SortedSet<Key> pscan = ProductManifest.scan(zipHive);
                    Set<Key> mscan = zipHive.execute(new ManifestListOperation());
                    dto.details = "Hive with " + mscan.size() + " manifest(s) "
                            + (pscan.isEmpty() ? "" : "(" + pscan.size() + " product(s)) ");
                } catch (Exception e) {
                    log.error("not a hive", e);
                    dto.isHive = false;
                }
            }

        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
        }
        return dto;
    }

    @Override
    public UploadInfoDto importRawContent(UploadInfoDto dto) {
        if (dto.tag != null && !dto.tag.equals(dto.tag.trim())) {
            throw new WebApplicationException("Tag contains leading or trailing whitespace: '" + dto.tag + "'",
                    Status.EXPECTATION_FAILED);
        }
        if (dto.name != null && !dto.name.equals(dto.name.trim())) {
            throw new WebApplicationException("Name contains leading or trailing whitespace: '" + dto.name + "'",
                    Status.EXPECTATION_FAILED);
        }

        try {
            Path targetFile = minion.getDownloadDir().resolve(dto.tmpFilename);
            if (dto.isHive) {
                doImportHive(dto, targetFile);
            } else if (dto.isProduct) {
                doImportProduct(dto, targetFile);
            } else {
                doImport(dto, targetFile);
            }
            PathHelper.deleteRecursiveRetry(targetFile);

            changes.create(ObjectChangeType.SOFTWARE_PACKAGE, Map.of(ObjectChangeDetails.CHANGE_HINT, this.repository));

            return dto;
        } catch (IOException e) {
            throw new WebApplicationException("Failed to import file: " + e.getMessage(), Status.BAD_REQUEST);
        }
    }

    private void doImport(UploadInfoDto dto, Path targetFile) throws IOException {
        try (FileSystem zfs = PathHelper.openZip(targetFile)) {
            Path zroot = zfs.getPath("/");
            if (dto.supportedOperatingSystems == null) {
                Manifest.Key key = new Manifest.Key(dto.name, dto.tag);
                Set<Manifest.Key> existing = hive.execute(new ManifestListOperation());
                if (!existing.contains(key)) {
                    try (Transaction t = hive.getTransactions().begin()) {
                        hive.execute(new ImportOperation().setSourcePath(zroot).setManifest(key));
                    }
                    dto.details = "Import of " + key + " successful";
                } else {
                    dto.details = "Skipped import of " + dto.name + ":" + dto.tag + ". Software already exists!";
                }
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append("Import of " + dto.name + ":" + dto.tag + " on ");
                int count = 0;
                for (OperatingSystem os : dto.supportedOperatingSystems) {
                    ScopedManifestKey key = new ScopedManifestKey(dto.name, os, dto.tag);

                    Set<Manifest.Key> existing = hive.execute(new ManifestListOperation());
                    if (!existing.contains(key.getKey())) {
                        try (Transaction t = hive.getTransactions().begin()) {
                            hive.execute(new ImportOperation().setSourcePath(zroot).setManifest(key.getKey()));
                        }
                        builder.append((count++ == 0 ? "" : ", ") + os.name());
                    }
                }
                dto.details = builder.toString() + " successful";
                if (count == 0) {
                    dto.details = "Skipped import of " + dto.name + ":" + dto.tag + ". Software already exists!";
                }
            }
        }
    }

    private void doImportProduct(UploadInfoDto dto, Path targetFile) throws IOException {
        try (FileSystem zfs = PathHelper.openZip(targetFile)) {
            // If we ever want to resolve dependencies from software repos, we need a master URL here (RemoteService).
            DependencyFetcher fetcher = new LocalDependencyFetcher();

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

            Manifest.Key prodKey = new Manifest.Key(pd.product + "/product", pvd.version);
            Set<Manifest.Key> existing = hive.execute(new ManifestListOperation());
            if (!existing.contains(prodKey)) {
                ProductManifestBuilder.importFromDescriptor(desc, hive, fetcher, false);
                dto.details = "Product (" + pd.name + ", " + pvd.version + ") imported";
            } else {
                dto.details = "Skipped import of Product (" + pd.name + ", " + pvd.version + "). Product already exists!";
            }
        }
    }

    private void doImportHive(UploadInfoDto dto, Path targetFile) {
        // import full hive
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
        try (BHive zipHive = new BHive(targetUri, null, new ActivityReporter.Null())) {
            Set<Key> manifestKeys = zipHive.execute(new ManifestListOperation());
            if (manifestKeys.isEmpty()) {
                throw new WebApplicationException("ZIP file does not contain a manifest.", Status.BAD_REQUEST);
            }

            // Determine required objects
            CopyOperation copy = new CopyOperation().setDestinationHive(hive);
            ObjectListOperation scan = new ObjectListOperation();
            int mcount = 0;
            for (Key manifestKey : manifestKeys) {
                // Ignore existing software
                if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(manifestKey)))) {
                    continue;
                }
                copy.addManifest(manifestKey);
                scan.addManifest(manifestKey);
                mcount++;
            }

            // Add all required artifacts
            Set<ObjectId> objectIds = zipHive.execute(scan);
            objectIds.forEach(copy::addObject);

            // Execute import only if we have something to do
            if (mcount > 0) {
                zipHive.execute(copy);
            }
            dto.details = mcount + " Manifests imported";
        }
    }

    private void assertNullOrRelativePath(String p) {
        if (p == null) {
            return;
        }
        RuntimeAssert.assertFalse(p.contains("..") || p.startsWith("/"), String.format(RELPATH_ERROR, p));
    }

}
