package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
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
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareResource;

public class SoftwareResourceImpl implements SoftwareResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareResourceImpl.class);

    @Context
    private ResourceContext rc;

    @Inject
    private Minion minion;

    @Inject
    private VersionSorterService vss;

    private final BHive hive;

    public SoftwareResourceImpl(BHive hive) {
        this.hive = hive;
    }

    @Override
    public List<Manifest.Key> list(boolean products, boolean generic) {
        List<Manifest.Key> result = new ArrayList<>();
        SortedSet<Key> keySet = hive.execute(new ManifestListOperation());
        for (Manifest.Key k : keySet) {
            if (SoftwareRepositoryManifest.isSoftwareRepositoryManifest(k)) {
                continue;
            }
            if (products && generic) {
                result.add(k);
            } else {
                Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(k));
                boolean isProduct = mf.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL);
                if (products && isProduct || generic && !isProduct) {
                    result.add(k);
                }
            }
        }
        result.sort(vss.getKeyComparator(null, null));
        return result;
    }

    @Override
    public String getSoftwareDiskUsage(String name) {
        SortedSet<Key> mfs = hive.execute(new ManifestListOperation().setManifestName(name));

        ObjectListOperation olo = new ObjectListOperation();
        mfs.forEach(olo::addManifest);
        SortedSet<ObjectId> objs = hive.execute(olo);

        ObjectSizeOperation oso = new ObjectSizeOperation();
        objs.forEach(oso::addObject);
        return UnitHelper.formatFileSize(hive.execute(oso));
    }

    @Override
    public void delete(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        SortedSet<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
        if (existing.size() != 1) {
            log.warn("Cannot uniquely identify {} to delete", key);
            return;
        }

        hive.execute(new ManifestDeleteOperation().setToDelete(key));
    }

    @Override
    public String createSoftwareZipFile(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);

        // Determine required objects
        ObjectListOperation scan = new ObjectListOperation();
        scan.addManifest(key);
        SortedSet<ObjectId> objectIds = hive.execute(scan);

        // Copy objects into the target hive
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        String token = ds.createNewToken();
        Path targetFile = ds.getStoragePath(token);
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
        try (BHive zipHive = new BHive(targetUri, new ActivityReporter.Null())) {
            CopyOperation op = new CopyOperation().setDestinationHive(zipHive);
            op.addManifest(key);
            objectIds.forEach(op::addObject);
            hive.execute(op);
        }
        ds.registerForDownload(token, key.directoryFriendlyName() + ".zip");
        return token;
    }

    @Override
    public List<Manifest.Key> upload(InputStream inputStream) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        try {
            List<Manifest.Key> imported = new ArrayList<>();

            // Download the hive to a temporary location
            Files.copy(inputStream, targetFile);

            // Read all product manifests
            URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
            try (BHive zipHive = new BHive(targetUri, new ActivityReporter.Null())) {
                SortedSet<Key> manifestKeys = zipHive.execute(new ManifestListOperation());
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
                SortedSet<ObjectId> objectIds = zipHive.execute(scan);
                objectIds.forEach(copy::addObject);

                // Execute import only if we have something to do
                if (!imported.isEmpty()) {
                    zipHive.execute(copy);
                }
                return imported;
            }
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
        } finally {
            PathHelper.deleteRecursive(targetFile);
        }
    }

    @Override
    public List<Key> uploadRawContent(InputStream inputStream, String manifestName, String manifestTag, String supportedOS) {
        List<Key> result = new ArrayList<>();

        String tmpRawContent = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpRawContent);
        try {
            Files.copy(inputStream, targetFile);

            try (FileSystem zfs = PathHelper.openZip(targetFile)) {
                Path zroot = zfs.getPath("/");
                for (String os : supportedOS.split(",")) {
                    ScopedManifestKey key = new ScopedManifestKey(manifestName, OperatingSystem.valueOf(os), manifestTag);

                    SortedSet<Manifest.Key> existing = hive.execute(new ManifestListOperation());
                    if (!existing.contains(key.getKey())) {
                        hive.execute(new ImportOperation().setSourcePath(zroot).setManifest(key.getKey()));
                        result.add(key.getKey());
                    }
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
        } finally {
            PathHelper.deleteRecursive(targetFile);
        }
        return result;
    }
}
