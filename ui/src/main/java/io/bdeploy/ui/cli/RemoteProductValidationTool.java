package io.bdeploy.ui.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.cli.RemoteProductValidationTool.ProductValidationDescriptorConfig;
import io.bdeploy.ui.dto.ProductValidationDescriptor;
import io.bdeploy.ui.dto.ProductValidationResponseDto;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

@Help("Validate product config")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-product-validation")
public class RemoteProductValidationTool extends RemoteServiceTool<ProductValidationDescriptorConfig> {

    public @interface ProductValidationDescriptorConfig {

        @Help("Descriptor file path")
        String descriptorFile();
    }

    public RemoteProductValidationTool() {
        super(ProductValidationDescriptorConfig.class);
    }

    @Override
    protected RenderableResult run(ProductValidationDescriptorConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.descriptorFile(), "--descriptorFile path missing");

        var descriptor = Paths.get(config.descriptorFile());
        if (!Files.exists(descriptor)) {
            throw new IllegalStateException("File " + descriptor + " does not exist");
        }

        var yaml = parseDescriptorFile(descriptor);

        var zipFile = zipFiles(descriptor.getParent(), yaml);

        try {
            return validate(remote, zipFile);
        } finally {
            PathHelper.deleteRecursive(zipFile);
        }
    }

    private ProductValidationDescriptor parseDescriptorFile(Path descriptor) {
        try (InputStream is = Files.newInputStream(descriptor)) {
            return StorageHelper.fromYamlStream(is, ProductValidationDescriptor.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + descriptor, e);
        }
    }

    private Path zipFiles(Path dir, ProductValidationDescriptor originalDescriptor) {
        Path zipFile;
        try {
            zipFile = Files.createTempFile("cli_validation_", ".zip");
        } catch (IOException e) {
            throw new IllegalStateException("Filed to create temp file", e);
        }
        var zippedDescriptor = new ProductValidationDescriptor();
        Function<String, Path> toPath = (filename) -> dir.resolve(filename).toAbsolutePath();

        var dirEntries = new HashSet<String>();

        try (OutputStream fos = Files.newOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
            // zip product-info.yaml
            var productSrcFile = toPath.apply(originalDescriptor.product);
            zippedDescriptor.product = copyIntoZip(productSrcFile, zos, dirEntries);

            // zip references from product-info.yaml
            zipProductInfoReferences(productSrcFile, zos, dirEntries);

            // zip applications mentioned in descriptor file
            Map<String, String> applications = originalDescriptor.applications == null ? Collections.emptyMap()
                    : originalDescriptor.applications;
            for (String application : applications.keySet()) {
                var applicationSrcFile = toPath.apply(applications.get(application));
                var applicationTargetFilename = UUID.randomUUID().toString() + "_" + applicationSrcFile.getFileName();
                var applicationTargetFile = copyIntoZip(applicationSrcFile, applicationTargetFilename, zos, dirEntries);
                zippedDescriptor.applications.put(application, applicationTargetFile);
            }

            String descriptorStr = JacksonHelper.createObjectMapper(MapperType.YAML).writeValueAsString(zippedDescriptor);

            InputStream descriptorInputStream = new ByteArrayInputStream(descriptorStr.getBytes(Charset.forName("UTF-8")));

            copyIntoZip(descriptorInputStream, ProductValidationDescriptor.FILE_NAME, zos, dirEntries);

            return zipFile;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create ZIP file " + zipFile, e);
        }
    }

    private void zipProductInfoReferences(Path productInfoPath, ZipOutputStream zos, Set<String> dirEntries) {
        ProductDescriptor prod;
        try (InputStream is = Files.newInputStream(productInfoPath)) {
            prod = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
            List<String> paths = Stream
                    .of(prod.applicationTemplates, prod.instanceTemplates, prod.instanceVariableTemplates,
                            prod.parameterTemplates)
                    .filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());

            for (String pathStr : paths) {
                Path path = productInfoPath.getParent().resolve(pathStr);
                this.copyIntoZip(path, pathStr, zos, dirEntries);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + productInfoPath, e);
        }
    }

    private String copyIntoZip(Path srcFile, ZipOutputStream zos, Set<String> dirEntries) throws IOException {
        try (InputStream fis = Files.newInputStream(srcFile)) {
            return copyIntoZip(fis, srcFile.getFileName().toString(), zos, dirEntries);
        }
    }

    private String copyIntoZip(Path srcFile, String targetFilename, ZipOutputStream zos, Set<String> dirEntries)
            throws IOException {
        try (InputStream fis = Files.newInputStream(srcFile)) {
            return copyIntoZip(fis, targetFilename, zos, dirEntries);
        }
    }

    private String copyIntoZip(InputStream is, String targetFilename, ZipOutputStream zos, Set<String> dirEntries)
            throws IOException {

        // Create directory entries for target file
        String[] dirs = targetFilename.replace("\\", "/").split("/");
        String dirname = "";
        for (int i = 0; i < dirs.length - 1; i++) {
            dirname += dirs[i] + "/";
            if (dirEntries.contains(dirname)) {
                continue;
            }
            zos.putNextEntry(new ZipEntry(dirname));
            dirEntries.add(dirname);
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

    private DataResult validate(RemoteService svc, Path zipFile) {
        if (!Files.isRegularFile(zipFile)) {
            helpAndFail("zip file is not a regular file");
        }

        try (InputStream is = Files.newInputStream(zipFile)) {
            try (MultiPart mp = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("file", is);
                bp.setFilename("file.zip");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                mp.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget(new JerseyOnBehalfOfFilter(getLocalContext()))
                        .path("/product-validation");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    return createResultWithErrorMessage("Failed to validate. " + response.getStatusInfo().getReasonPhrase());
                }
                ProductValidationResponseDto validationResult = response.readEntity(ProductValidationResponseDto.class);
                if (validationResult.errors.isEmpty()) {
                    return createResultWithSuccessMessage("Product is valid");
                } else {
                    return createResultWithErrorMessage("Product is invalid. " + String.join(" ", validationResult.errors));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot upload zip file", e);
        }
    }

}
