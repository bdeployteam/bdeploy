package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.TrackingTemplateOverrideResolver;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.schema.PublicSchemaValidator;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductValidationResource;
import io.bdeploy.ui.dto.ProductValidationConfigDescriptor;
import jakarta.inject.Inject;

public class ProductValidationResourceImpl implements ProductValidationResource {

    @Inject
    private Minion root;

    @Override
    public ProductValidationResponseApi validate(FormDataMultiPart fdmp) {
        try {
            return validate(parse(FormDataHelper.getStreamFromMultiPart(fdmp)));
        } catch (SchemaValidationException ex) {
            return new ProductValidationResponseApi(ex.errors.stream()
                    .map(e -> new ProductValidationIssueApi(ProductValidationSeverity.ERROR, ex.path + ": " + e)).toList());
        }
    }

    private ProductValidationResponseApi validate(ProductValidationConfigDescriptor config) {
        List<ProductValidationIssueApi> issues = new ArrayList<>();

        // validate all application commands, parameters, etc.
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

        // validate all the templates.
        issues.addAll(validateInstanceVariableDefinitions(config));
        issues.addAll(validateInstanceTemplates(config));
        issues.addAll(validateApplicationTemplates(config));

        return new ProductValidationResponseApi(issues);
    }

    private static List<ProductValidationIssueApi> validateInstanceVariableDefinitions(ProductValidationConfigDescriptor desc) {
        Set<String> ids = new HashSet<>();
        Set<String> duplicateIds = desc.instanceVariableDefinitions.stream().flatMap(ivd -> ivd.definitions.stream())
                .map(descriptor -> descriptor.id).filter(id -> !ids.add(id)).collect(Collectors.toSet());
        return duplicateIds.isEmpty() ? Collections.emptyList()
                : Collections.singletonList(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Duplicate instance variable definition IDs: " + String.join(", ", duplicateIds)));
    }

    private List<ProductValidationIssueApi> validateInstanceTemplates(ProductValidationConfigDescriptor desc) {
        return desc.instanceTemplates.stream().map(t -> {
            try {
                var issues = validateFlatInstanceTemplate(
                        new FlattenedInstanceTemplateConfiguration(t, desc.instanceVariableTemplates, desc.applicationTemplates),
                        desc);

                issues.addAll(validateTemplateVariablesOnInstance(t, desc));

                return issues;
            } catch (Exception e) {
                return Collections.singletonList(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Cannot resolve instance template " + t.name + ": " + e.toString()));
            }
        }).flatMap(l -> l.stream()).filter(Objects::nonNull).toList();
    }

    private List<ProductValidationIssueApi> validateApplicationTemplates(ProductValidationConfigDescriptor desc) {
        return desc.applicationTemplates.stream().map(a -> {
            try {
                var issues = validateFlatApplicationTemplate(
                        new FlattenedApplicationTemplateConfiguration(a, desc.applicationTemplates, null), desc);

                // need to validate variables directly on the original, as flattening moves/merges variables.
                issues.addAll(validateTemplateVariablesOnApplication(a));

                return issues;
            } catch (Exception e) {
                return Collections.singletonList(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Cannot resolve application template " + a.name + ": " + e.toString()));
            }
        }).flatMap(l -> l.stream()).filter(Objects::nonNull).toList();
    }

    private Collection<? extends ProductValidationIssueApi> validateTemplateVariablesOnInstance(InstanceTemplateDescriptor t,
            ProductValidationConfigDescriptor desc) {
        List<ProductValidationIssueApi> result = new ArrayList<>();

        // validate on instanceVariable values, application names and application startParameters
        TrackingTemplateOverrideResolver res = new TrackingTemplateOverrideResolver(Collections.emptyList());
        for (var v : t.instanceVariables) {
            visitSingleInstanceVariable(v, desc, res, t.instanceVariableDefaults);
        }

        for (var group : t.groups) {
            for (var app : group.applications) {
                TemplateHelper.process(app.name, res, res::canResolve);
                for (var p : app.startParameters) {
                    TemplateHelper.process(p.value, res, res::canResolve);
                }
            }
        }

        // now compare to the list of *defined* variables to find missing and/or too many definitions.
        Set<String> requested = res.getRequestedVariables();
        for (var r : requested) {
            var tv = t.templateVariables.stream().filter(v -> v.id.equals(r)).findAny();
            if (tv.isEmpty()) {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Missing definition for used template variable " + r + " in instance template " + t.name));
            }
        }

        for (var d : t.templateVariables) {
            if (!requested.contains(d.id)) {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.WARNING,
                        "Template variable " + d.id + " defined but never used in instance template " + t.name));
            }
        }

        return result;
    }

    private static List<ProductValidationIssueApi> visitSingleInstanceVariable(TemplateableVariableConfiguration tvc,
            ProductValidationConfigDescriptor desc, TrackingTemplateOverrideResolver res,
            List<TemplateableVariableDefaultConfiguration> defaults) {
        List<ProductValidationIssueApi> result = new ArrayList<>();

        if (tvc.template == null || tvc.template.isBlank()) {
            // it is not a template - check if there is an override in the template directly.
            LinkedValueConfiguration val = tvc.value;
            var override = defaults.stream().filter(d -> d.id.equals(tvc.id)).findAny();
            if (override.isPresent()) {
                val = override.get().value;
            }

            // if either value or override is set, collect variables from there.
            if (val != null) {
                TemplateHelper.process(val.getPreRenderable(), res, res::canResolve);
            }
        } else {
            // find the template and validate each variable in there recursively.
            var tpl = desc.instanceVariableTemplates.stream().filter(t -> t.id.equals(tvc.template)).findFirst();
            if (!tpl.isPresent()) {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Cannot find instance variable template with ID " + tvc.template));
            } else {
                for (var tplv : tpl.get().instanceVariables) {
                    result.addAll(visitSingleInstanceVariable(tplv, desc, res, defaults));
                }
            }
        }

        return result;
    }

    private static Collection<? extends ProductValidationIssueApi> validateTemplateVariablesOnApplication(
            ApplicationTemplateDescriptor a) {
        List<ProductValidationIssueApi> result = new ArrayList<>();

        String tplNiceName = a.name == null ? createNiceAnonymousName(a.application) : a.name;

        // figure out which variables are requested in the template.
        TrackingTemplateOverrideResolver res = new TrackingTemplateOverrideResolver(Collections.emptyList());
        TemplateHelper.process(a.name, res, res::canResolve);
        for (var p : a.startParameters) {
            TemplateHelper.process(p.value, res, res::canResolve);
        }

        // now compare to the list of *defined* variables to find missing and/or too many definitions.
        Set<String> requested = res.getRequestedVariables();
        for (var r : requested) {
            var tv = a.templateVariables.stream().filter(v -> v.id.equals(r)).findAny();
            if (tv.isEmpty()) {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Missing definition for used template variable " + r + " in " + tplNiceName));
            }
        }

        for (var d : a.templateVariables) {
            if (!requested.contains(d.id)) {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.WARNING,
                        "Template variable " + d.id + " defined but never used in " + tplNiceName));
            }
        }

        return result;
    }

    private List<ProductValidationIssueApi> validateFlatInstanceTemplate(FlattenedInstanceTemplateConfiguration tpl,
            ProductValidationConfigDescriptor descriptor) {
        List<String> controlGroupsInTemplate = tpl.processControlGroups.stream().map(c -> c.name).toList();
        List<ProductValidationIssueApi> result = new ArrayList<>();

        for (var grp : tpl.groups) {
            for (var app : grp.applications) {
                if (controlGroupsInTemplate != null && !controlGroupsInTemplate.isEmpty()
                        && app.preferredProcessControlGroup != null
                        && !controlGroupsInTemplate.contains(app.preferredProcessControlGroup)) {
                    result.add(new ProductValidationIssueApi(ProductValidationSeverity.WARNING,
                            "Preferred process control group '" + app.preferredProcessControlGroup + "' for application "
                                    + (app.name == null ? createNiceAnonymousName(app.application) : app.name)
                                    + " is not available in the instance template " + tpl.name));
                }

                result.addAll(validateFlatApplicationTemplate(app, descriptor));
            }
        }

        return result;
    }

    private static List<ProductValidationIssueApi> validateFlatApplicationTemplate(FlattenedApplicationTemplateConfiguration tpl,
            ProductValidationConfigDescriptor descriptor) {
        List<ProductValidationIssueApi> result = new ArrayList<>();

        String tplNiceName = tpl.name == null ? createNiceAnonymousName(tpl.application) : tpl.name;

        if (!descriptor.applications.containsKey(tpl.application)) {
            result.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                    "Application " + tpl.application + " not found for application template " + tplNiceName));
        }

        return result;
    }

    private static String createNiceAnonymousName(String s) {
        return "<anonymous> (" + s + ")";
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

    private static List<ParameterDescriptor> expandTemplateRecursive(String app, ParameterDescriptor param,
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
            PathHelper.deleteRecursiveRetry(tmpDir);
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
        config.instanceVariableDefinitions = parse(dir, dir, config.product.instanceVariableDefinitions,
                InstanceVariableDefinitionDescriptor.class, validator, Schema.instanceVariableDefinitionYaml);

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

    private static <T> T parse(Path root, Path path, Class<T> klass, PublicSchemaValidator validator, Schema schema) {
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
        private final transient List<String> errors;
        private final transient Path path;

        public SchemaValidationException(Path path, List<String> errors) {
            this.path = path;
            this.errors = errors;
        }
    }
}
