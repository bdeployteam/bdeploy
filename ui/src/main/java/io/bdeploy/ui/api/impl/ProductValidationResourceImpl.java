package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductValidationResource;
import io.bdeploy.ui.dto.ProductValidationConfigDescriptor;
import io.bdeploy.ui.dto.ProductValidationDescriptor;
import io.bdeploy.ui.dto.ProductValidationResponseDto;
import jakarta.inject.Inject;

public class ProductValidationResourceImpl implements ProductValidationResource {

    @Inject
    private Minion root;

    @Override
    public ProductValidationResponseDto validate(InputStream inputStream) {
        ProductValidationConfigDescriptor config = parse(inputStream);
        return validate(config);
    }

    private ProductValidationResponseDto validate(ProductValidationConfigDescriptor config) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, ApplicationDescriptor> appEntry : config.applications.entrySet()) {
            var app = appEntry.getKey();
            var applicationDescriptor = appEntry.getValue();
            if (applicationDescriptor.startCommand != null) {
                var startIds = new HashSet<String>();
                for (ParameterDescriptor param : applicationDescriptor.startCommand.parameters) {
                    if (startIds.contains(param.id)) {
                        errors.add(app + " has start command parameters with duplicate id " + param.id);
                    }
                    startIds.add(param.id);
                }
            }

            if (applicationDescriptor.stopCommand != null) {
                var stopIds = new HashSet<String>();
                for (ParameterDescriptor param : applicationDescriptor.stopCommand.parameters) {
                    if (stopIds.contains(param.id)) {
                        errors.add(app + " has stop command parameters with duplicate id " + param.id);
                    }
                    stopIds.add(param.id);
                }
            }
        }
        return new ProductValidationResponseDto(errors);
    }

    private ProductValidationConfigDescriptor parse(InputStream inputStream) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "prodVal-");
            Path zip = tmpDir.resolve("product.zip");
            Path unzipped = tmpDir.resolve("unzipped_product");
            Files.copy(inputStream, zip);
            ZipHelper.unzip(zip, unzipped);
            return parse(unzipped);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

    private ProductValidationConfigDescriptor parse(Path dir) {
        ProductValidationConfigDescriptor config = new ProductValidationConfigDescriptor();

        config.productValidation = parse(dir.resolve(ProductValidationDescriptor.FILE_NAME), ProductValidationDescriptor.class);
        config.product = parse(dir.resolve(config.productValidation.product), ProductDescriptor.class);
        config.applicationTemplates = parse(dir, config.product.applicationTemplates, ApplicationTemplateDescriptor.class);
        config.instanceTemplates = parse(dir, config.product.instanceTemplates, InstanceTemplateDescriptor.class);
        config.instanceVariableTemplates = parse(dir, config.product.instanceVariableTemplates,
                InstanceVariableTemplateDescriptor.class);
        config.parameterTemplates = parse(dir, config.product.parameterTemplates, ParameterTemplateDescriptor.class);

        config.applications = new HashMap<>();
        var apps = Optional.ofNullable(config.productValidation.applications).orElseGet(Collections::emptyMap);
        for (String app : apps.keySet()) {
            Path appPath = dir.resolve(apps.get(app));
            var appDescriptor = parse(appPath, ApplicationDescriptor.class);
            config.applications.put(app, appDescriptor);
        }

        return config;
    }

    private <T> List<T> parse(Path dir, List<String> filenames, Class<T> klass) {
        return filenames == null ? Collections.emptyList()
                : filenames.stream().map(filename -> dir.resolve(filename)).map(path -> parse(path, klass))
                        .collect(Collectors.toList());
    }

    private <T> T parse(Path path, Class<T> klass) {
        try (InputStream is = Files.newInputStream(path)) {
            return StorageHelper.fromYamlStream(is, klass);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + path, e);
        }
    }

}
