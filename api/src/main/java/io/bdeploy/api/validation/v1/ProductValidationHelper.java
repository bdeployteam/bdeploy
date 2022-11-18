package io.bdeploy.api.validation.v1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.JerseyClientFactory;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 * A helper that handles packaging a local {@link ProductValidationDescriptorApi} along with all required files, send it to the
 * server, and return the validation result.
 */
public class ProductValidationHelper {

    private ProductValidationHelper() {
    }

    /**
     * @param descriptor the path to a {@link ProductValidationDescriptorApi}
     * @param remote the remote server to validate on.
     * @return the validation result in form of a {@link ProductValidationResponseApi}.
     */
    public static ProductValidationResponseApi validate(Path descriptor, RemoteService remote) {
        ProductValidationDescriptorApi desc = parseDescriptor(descriptor);
        Path zipFile = zipFiles(descriptor.toAbsolutePath().getParent(), desc);

        try {
            return validateRemote(remote, zipFile);
        } finally {
            PathHelper.deleteRecursive(zipFile);
        }
    }

    private static ProductValidationDescriptorApi parseDescriptor(Path descriptor) {
        try (InputStream is = Files.newInputStream(descriptor)) {
            return StorageHelper.fromYamlStream(is, ProductValidationDescriptorApi.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + descriptor, e);
        }
    }

    private static Path zipFiles(Path dir, ProductValidationDescriptorApi originalDescriptor) {
        Path zipFile;
        try {
            zipFile = Files.createTempFile("validation_", ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp file", e);
        }

        ProductValidationDescriptorApi zippedDescriptor = new ProductValidationDescriptorApi();
        Function<String, Path> toPath = f -> dir.resolve(f).toAbsolutePath();
        Set<String> dirEntries = new HashSet<>();

        try (OutputStream fos = Files.newOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
            // zip product-info.yaml
            Path productSrcFile = toPath.apply(originalDescriptor.product);

            if (!Files.isRegularFile(productSrcFile)) {
                throw new IllegalStateException("Product validation descriptor is not a file: " + productSrcFile);
            }

            zippedDescriptor.product = copyIntoZip(productSrcFile, zos, dirEntries);

            // zip references from product-info.yaml
            zipProductInfoReferences(productSrcFile, zos, dirEntries);

            // zip applications mentioned in descriptor file
            Map<String, String> applications = originalDescriptor.applications == null ? Collections.emptyMap()
                    : originalDescriptor.applications;
            for (Map.Entry<String, String> entry : applications.entrySet()) {
                String application = entry.getKey();
                String appFileName = entry.getValue();
                Path applicationSrcFile = toPath.apply(appFileName);

                if (!Files.isRegularFile(applicationSrcFile)) {
                    throw new IllegalStateException(
                            "Application descriptor for '" + application + "' is not a file: " + applicationSrcFile);
                }

                String applicationTargetFilename = application + "_" + applicationSrcFile.getFileName();
                String applicationTargetFile = copyIntoZip(applicationSrcFile, applicationTargetFilename, zos, dirEntries);
                zippedDescriptor.applications.put(application, applicationTargetFile);
            }

            try (InputStream is = new ByteArrayInputStream(StorageHelper.toRawYamlBytes(zippedDescriptor))) {
                copyIntoZip(is, ProductValidationDescriptorApi.FILE_NAME, zos, dirEntries);
            }

            return zipFile;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create ZIP file " + zipFile, e);
        }
    }

    private static void zipProductInfoReferences(Path productInfoPath, ZipOutputStream zos, Set<String> dirEntries) {
        ProductDescriptor prod;
        try (InputStream is = Files.newInputStream(productInfoPath)) {
            prod = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
            List<String> paths = Stream
                    .of(prod.applicationTemplates, prod.instanceTemplates, prod.instanceVariableTemplates,
                            prod.parameterTemplates)
                    .filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());

            for (String pathStr : paths) {
                Path path = productInfoPath.getParent().resolve(pathStr);

                if (!Files.isRegularFile(path)) {
                    throw new IllegalStateException("Descriptor referenced from product is not a file: " + path);
                }

                copyIntoZip(path, pathStr, zos, dirEntries);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + productInfoPath, e);
        }
    }

    private static String copyIntoZip(Path srcFile, ZipOutputStream zos, Set<String> dirEntries) throws IOException {
        try (InputStream fis = Files.newInputStream(srcFile)) {
            return copyIntoZip(fis, srcFile.getFileName().toString(), zos, dirEntries);
        }
    }

    private static String copyIntoZip(Path srcFile, String targetFilename, ZipOutputStream zos, Set<String> dirEntries)
            throws IOException {
        try (InputStream fis = Files.newInputStream(srcFile)) {
            return copyIntoZip(fis, targetFilename, zos, dirEntries);
        }
    }

    private static String copyIntoZip(InputStream is, String targetFilename, ZipOutputStream zos, Set<String> dirEntries)
            throws IOException {

        // Create directory entries for target file
        String[] dirs = targetFilename.replace("\\", "/").split("/");
        StringBuilder dirname = new StringBuilder();
        for (int i = 0; i < dirs.length - 1; i++) {
            dirname.append(dirs[i]).append("/");
            String level = dirname.toString();

            if (dirEntries.contains(level)) {
                continue;
            }
            zos.putNextEntry(new ZipEntry(level));
            dirEntries.add(level);
        }

        // Start writing a new file entry
        zos.putNextEntry(new ZipEntry(targetFilename));

        int length;
        // create byte buffer
        byte[] buffer = new byte[1024];

        // read and write the content of the file
        while ((length = is.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }
        // current file entry is written and current zip entry is closed
        zos.closeEntry();

        // close the InputStream of the file
        is.close();

        return targetFilename;
    }

    private static ProductValidationResponseApi validateRemote(RemoteService svc, Path zipFile) {
        if (!Files.isRegularFile(zipFile)) {
            throw new IllegalStateException("zip file is not a regular file");
        }

        try (InputStream is = Files.newInputStream(zipFile)) {
            try (MultiPart mp = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("file", is);
                bp.setFilename("file.zip");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                mp.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget().path("/public/v1/validation");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    return new ProductValidationResponseApi(Collections.singletonList(new ProductValidationIssueApi(
                            ProductValidationSeverity.ERROR, "Cannot validate: " + response.getStatusInfo().getReasonPhrase())));
                }
                return response.readEntity(ProductValidationResponseApi.class);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot upload zip file", e);
        }
    }

}
