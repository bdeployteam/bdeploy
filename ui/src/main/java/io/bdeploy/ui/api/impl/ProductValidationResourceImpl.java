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

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.schema.PublicSchemaValidator;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductValidationResource;
import io.bdeploy.ui.dto.ProductValidationConfigDescriptor;
import jakarta.inject.Inject;

public class ProductValidationResourceImpl implements ProductValidationResource {

    @Inject
    private Minion root;

    @Override
    public ProductValidationResponseApi validate(InputStream inputStream) {
        try {
            return validate(parse(inputStream));
        } catch (SchemaValidationException ex) {
            return new ProductValidationResponseApi(ex.errors.stream()
                    .map(e -> new ProductValidationIssueApi(ProductValidationSeverity.ERROR, ex.path + ": " + e)).toList());
        }
    }

    private ProductValidationResponseApi validate(ProductValidationConfigDescriptor config) {
        List<ProductValidationIssueApi> issues = new ArrayList<>();
        for (Map.Entry<String, ApplicationDescriptor> appEntry : config.applications.entrySet()) {
            var app = appEntry.getKey();
            var applicationDescriptor = appEntry.getValue();
            if (applicationDescriptor.startCommand != null) {
                validateCommand(issues, app, applicationDescriptor.startCommand, config.parameterTemplates);
            }

            if (applicationDescriptor.stopCommand != null) {
                validateCommand(issues, app, applicationDescriptor.stopCommand, config.parameterTemplates);
            }
        }
        return new ProductValidationResponseApi(issues);
    }

    private void validateCommand(List<ProductValidationIssueApi> issues, String app, ExecutableDescriptor command,
            List<ParameterTemplateDescriptor> parameterTemplates) {

        // expand and verify parameter templates
        var expanded = new ArrayList<ParameterDescriptor>();
        for (var param : command.parameters) {
            if (param.id == null) {
                if (param.template == null) {
                    issues.add(new ProductValidationIssueApi(ProductValidationSeverity.WARNING,
                            app + " has parameter without id or template"));
                    continue;
                }

                expanded.addAll(expandTemplateRecursive(app, param, parameterTemplates, issues));
            } else {
                expanded.add(param);
            }
        }

        // now check all parameters.
        var startIds = new HashSet<String>();
        for (var param : expanded) {
            if (startIds.contains(param.id)) {
                issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        app + " has parameters with duplicate id " + param.id));
            }
            startIds.add(param.id);
        }
    }

    private List<ParameterDescriptor> expandTemplateRecursive(String app, ParameterDescriptor param,
            List<ParameterTemplateDescriptor> parameterTemplates, List<ProductValidationIssueApi> issues) {
        List<ParameterTemplateDescriptor> templates = parameterTemplates.stream().filter(t -> t.id.equals(param.template))
                .toList();
        if (templates.size() != 1) {
            issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR, app + " references parameter template "
                    + param.template + " which was found " + templates.size() + " times"));
            return Collections.emptyList();
        }

        var expanded = new ArrayList<ParameterDescriptor>();
        for (var p : templates.get(0).parameters) {
            if (p.id == null) {
                if (p.template != null) {
                    expanded.addAll(expandTemplateRecursive(app, p, parameterTemplates, issues));
                } else {
                    issues.add(new ProductValidationIssueApi(ProductValidationSeverity.WARNING,
                            "parameter template " + param.template + " has parameter without id or template"));
                }
            } else {
                expanded.add(p);
            }
        }

        return expanded;
    }

    private ProductValidationConfigDescriptor parse(InputStream inputStream) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "prodVal-");
            Path zip = tmpDir.resolve("product.zip");
            Path unzipped = tmpDir.resolve("unzipped_product");
            Files.copy(inputStream, zip);
            ZipHelper.unzip(zip, unzipped);

            PublicSchemaValidator validator = new PublicSchemaValidator();

            return parse(unzipped, validator);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

    private ProductValidationConfigDescriptor parse(Path dir, PublicSchemaValidator validator) {
        ProductValidationConfigDescriptor config = new ProductValidationConfigDescriptor();

        config.productValidation = parse(dir, dir.resolve(ProductValidationDescriptorApi.FILE_NAME),
                ProductValidationDescriptorApi.class, validator, Schema.productValidationYaml);
        config.product = parse(dir, dir.resolve(config.productValidation.product), ProductDescriptor.class, validator,
                Schema.productInfoYaml);
        config.applicationTemplates = parse(dir, dir, config.product.applicationTemplates, ApplicationTemplateDescriptor.class,
                validator, Schema.applicationTemplateYaml);
        config.instanceTemplates = parse(dir, dir, config.product.instanceTemplates, InstanceTemplateDescriptor.class, validator,
                Schema.instanceTemplateYaml);
        config.instanceVariableTemplates = parse(dir, dir, config.product.instanceVariableTemplates,
                InstanceVariableTemplateDescriptor.class, validator, Schema.instanceVariableTemplateYaml);
        config.parameterTemplates = parse(dir, dir, config.product.parameterTemplates, ParameterTemplateDescriptor.class,
                validator, Schema.parameterTemplateYaml);

        config.applications = new HashMap<>();
        var apps = Optional.ofNullable(config.productValidation.applications).orElseGet(Collections::emptyMap);
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            String app = entry.getKey();
            String relApp = entry.getValue();
            Path appPath = dir.resolve(relApp);
            var appDescriptor = parse(dir, appPath, ApplicationDescriptor.class, validator, Schema.appInfoYaml);
            config.applications.put(app, appDescriptor);
        }

        return config;
    }

    private <T> List<T> parse(Path root, Path dir, List<String> filenames, Class<T> klass, PublicSchemaValidator validator,
            Schema schema) {
        return filenames == null ? Collections.emptyList()
                : filenames.stream().map(dir::resolve).map(path -> parse(root, path, klass, validator, schema)).toList();
    }

    private <T> T parse(Path root, Path path, Class<T> klass, PublicSchemaValidator validator, Schema schema) {
        if (schema != null) {
            List<String> result = validator.validate(schema, path);
            if (!result.isEmpty()) {
                throw new SchemaValidationException(root.relativize(path), result);
            }
        }

        try (InputStream is = Files.newInputStream(path)) {
            return StorageHelper.fromYamlStream(is, klass);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + root.relativize(path), e);
        }
    }

    private static class SchemaValidationException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        final List<String> errors;
        final Path path;

        public SchemaValidationException(Path path, List<String> errors) {
            this.path = path;
            this.errors = errors;
        }

    }

}
