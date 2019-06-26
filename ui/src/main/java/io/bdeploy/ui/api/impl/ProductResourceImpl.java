package io.bdeploy.ui.api.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.ProductDto;

public class ProductResourceImpl implements ProductResource {

    private static final Logger log = LoggerFactory.getLogger(ProductResourceImpl.class);

    @Context
    private ResourceContext rc;

    @Inject
    private Minion minion;

    private final BHive hive;

    public ProductResourceImpl(BHive hive) {
        this.hive = hive;
    }

    @Override
    public List<ProductDto> list() {
        List<ProductDto> result = new ArrayList<>();
        SortedSet<Key> scan = ProductManifest.scan(hive);

        scan.stream().map(k -> ProductManifest.of(hive, k)).forEach(pm -> {
            result.add(ProductDto.create(pm));
        });

        return result;
    }

    @Override
    public void delete(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        SortedSet<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
        if (existing.size() != 1) {
            log.warn("Cannot uniquely identify {} to delete", key);
            return;
        }

        if (getProductUseCount(name, tag) > 0) {
            throw new WebApplicationException("Product version is still in use", Status.BAD_REQUEST);
        }

        // This assumes that no single application version is used in multiple products.
        ProductManifest pmf = ProductManifest.of(hive, key);
        SortedSet<Key> apps = pmf.getApplications();

        hive.execute(new ManifestDeleteOperation().setToDelete(key));
        apps.forEach(a -> hive.execute(new ManifestDeleteOperation().setToDelete(a)));
    }

    @Override
    public ApplicationResource getApplicationResource(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        return rc.initResource(new ApplicationResourceImpl(hive, key));
    }

    @Override
    public String getProductDiskUsage(String name) {
        SortedSet<Key> mfs = hive.execute(new ManifestListOperation().setManifestName(name));

        ObjectListOperation olo = new ObjectListOperation();
        mfs.forEach(olo::addManifest);
        SortedSet<ObjectId> objs = hive.execute(olo);

        ObjectSizeOperation oso = new ObjectSizeOperation();
        objs.forEach(oso::addObject);
        return UnitHelper.formatFileSize(hive.execute(oso));
    }

    @Override
    public Long getProductUseCount(String name, String tag) {
        Manifest.Key checkKey = new Manifest.Key(name, tag);
        SortedSet<Manifest.Key> scan = InstanceManifest.scan(hive, true);
        return scan.stream().map(k -> InstanceManifest.of(hive, k).getConfiguration()).map(c -> c.product)
                .filter(p -> p.equals(checkKey)).count();
    }

    @Override
    public String createProductZipFile(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);

        // Determine required objects
        ObjectListOperation scan = new ObjectListOperation();
        scan.addManifest(key);
        SortedSet<ObjectId> objectIds = hive.execute(scan);

        // Copy objects into the target hive
        String randomId = UuidHelper.randomId();
        Path targetFile = minion.getDownloadDir().resolve(randomId + ".zip");
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
        try (BHive zipHive = new BHive(targetUri, new ActivityReporter.Null())) {
            CopyOperation op = new CopyOperation().setDestinationHive(zipHive);
            op.addManifest(key);
            objectIds.forEach(op::addObject);
            hive.execute(op);
        }
        return randomId;
    }

    @Override
    public Response downloadProduct(String token) {
        // File must be downloaded within a given timeout
        Path targetFile = minion.getDownloadDir().resolve(token + ".zip");
        File file = targetFile.toFile();
        if (!file.isFile()) {
            throw new WebApplicationException("Token to download product is not valid any more.", Status.BAD_REQUEST);
        }

        long lastModified = file.lastModified();
        long validUntil = lastModified + TimeUnit.MINUTES.toMillis(5);
        if (System.currentTimeMillis() > validUntil) {
            throw new WebApplicationException("Token to download product is not valid any more.", Status.BAD_REQUEST);
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (InputStream is = Files.newInputStream(targetFile)) {
                    is.transferTo(output);

                    // Intentionally not in finally block to allow resuming of the download
                    PathHelper.deleteRecursive(targetFile);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe.toString());
                    }
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        // Load and attach metadata to give the file a nice name
        URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toUri()).build();
        try (BHive zipHive = new BHive(targetUri, new ActivityReporter.Null())) {
            Key productKey = ProductManifest.scan(zipHive).first();
            String fileName = productKey.directoryFriendlyName() + ".zip";

            ContentDisposition contentDisposition = ContentDisposition.type("attachement").size(file.length()).fileName(fileName)
                    .build();
            responeBuilder.header("Content-Disposition", contentDisposition);
            responeBuilder.header("Content-Length", file.length());
        }
        return responeBuilder.build();
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
                SortedSet<Key> productKeys = ProductManifest.scan(zipHive);
                if (productKeys.isEmpty()) {
                    throw new WebApplicationException("ZIP file does not contain a product.", Status.BAD_REQUEST);
                }

                // Determine required objects
                CopyOperation copy = new CopyOperation().setDestinationHive(hive);
                ObjectListOperation scan = new ObjectListOperation();
                for (Key productKey : productKeys) {
                    // Ignore existing products
                    if (hive.execute(new ManifestExistsOperation().setManifest(productKey))) {
                        continue;
                    }
                    imported.add(productKey);
                    copy.addManifest(productKey);
                    scan.addManifest(productKey);
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
}
