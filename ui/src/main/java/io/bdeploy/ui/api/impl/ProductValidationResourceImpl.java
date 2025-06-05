package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.EndpointsDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
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
        ProductValidationConfigDescriptor parsed;
        try {
            parsed = parse(FormDataHelper.getStreamFromMultiPart(fdmp));
        } catch (SchemaValidationException ex) {
            return new ProductValidationResponseApi(ex.errors.stream()
                    .map(e -> new ProductValidationIssueApi(ProductValidationSeverity.ERROR, ex.path + ": " + e)).toList());
        }
        return validate(parsed);
    }

    private static ProductValidationResponseApi validate(ProductValidationConfigDescriptor config) {
        List<ProductValidationIssueApi> issues = new ArrayList<>();

        // validate all application commands, parameters, etc.
        for (Map.Entry<String, ApplicationDescriptor> appEntry : config.applications.entrySet()) {
            var app = appEntry.getKey();
            var applicationDescriptor = appEntry.getValue();
            if (applicationDescriptor.startCommand != null) {
                validateCommand(issues, app, applicationDescriptor.startCommand, config.parameterTemplates, false);
            }
            if (applicationDescriptor.stopCommand != null) {
                validateCommand(issues, app, applicationDescriptor.stopCommand, config.parameterTemplates, true);
            }
            if (applicationDescriptor.type == ApplicationType.CLIENT) {
                EndpointsDescriptor endpointsDescr = applicationDescriptor.endpoints;
                if (endpointsDescr != null) {
                    List<HttpEndpoint> httpEndpoints = endpointsDescr.http;
                    if (httpEndpoints != null && !httpEndpoints.isEmpty()) {
                        issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                                app + " is a client application but has endpoints configured"));
                    }
                }
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

    private static List<ProductValidationIssueApi> validateInstanceTemplates(ProductValidationConfigDescriptor desc) {
        var instanceVariableDescriptors = desc.instanceVariableDefinitions.stream().flatMap(ds -> ds.definitions.stream())
                .toList();
        var issues = desc.instanceTemplates.stream().map(t -> {
            try {
                var subIssues = validateFlatInstanceTemplate(new FlattenedInstanceTemplateConfiguration(t,
                        desc.instanceVariableTemplates, desc.applicationTemplates, instanceVariableDescriptors), desc);

                subIssues.addAll(validateTemplateVariablesOnInstance(t, desc));

                return subIssues;
            } catch (Exception e) {
                return Collections.singletonList(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Cannot resolve instance template " + t.name + ": " + e.toString()));
            }
        }).flatMap(l -> l.stream()).filter(Objects::nonNull).collect(Collectors.toList());

        // check process control
        Map<String, String> appTemplateToAppId = desc.applicationTemplates.stream()
                .collect(Collectors.toMap(x -> x.id, x -> x.application));
        Map<String, ProcessControlDescriptor> appsToProcessControl = desc.applications.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().processControl));
        for (var instanceTemplate : desc.instanceTemplates) {
            for (var group : instanceTemplate.groups) {
                for (var templateApp : group.applications) {
                    String appName = templateApp.application;
                    boolean usesTemplate = false;
                    if (appName == null) {
                        appName = appTemplateToAppId.get(templateApp.template);
                        usesTemplate = true;
                    }
                    var processControlDescriptor = Optional.ofNullable(appsToProcessControl.get(appName))
                            .orElse(new ProcessControlDescriptor());
                    if (!processControlDescriptor.supportsKeepAlive) {
                        addInstanceTemplateProcessControlIssue(issues, templateApp, "keepAlive", instanceTemplate.name,
                                group.name, usesTemplate);
                    }
                    if (!processControlDescriptor.supportsAutostart) {
                        addInstanceTemplateProcessControlIssue(issues, templateApp, "autostart", instanceTemplate.name,
                                group.name, usesTemplate);
                    }
                }
            }
        }
        return issues;
    }

    private static List<ProductValidationIssueApi> validateApplicationTemplates(ProductValidationConfigDescriptor desc) {
        List<ProductValidationIssueApi> issues = desc.applicationTemplates.stream().map(a -> {
            try {
                var subIssues = validateFlatApplicationTemplate(
                        new FlattenedApplicationTemplateConfiguration(a, desc.applicationTemplates, null), desc);

                // need to validate variables directly on the original, as flattening moves/merges variables.
                subIssues.addAll(validateTemplateVariablesOnApplication(a));

                return subIssues;
            } catch (Exception e) {
                return Collections.singletonList(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Cannot resolve application template " + a.name + ": " + e.toString()));
            }
        }).flatMap(l -> l.stream()).filter(Objects::nonNull).collect(Collectors.toList());

        // check process control
        Map<String, ProcessControlDescriptor> appsToProcessControl = desc.applications.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().processControl));
        for (var appTemplate : desc.applicationTemplates) {
            var processControlDescriptor = Optional.ofNullable(appsToProcessControl.get(appTemplate.application))
                    .orElse(new ProcessControlDescriptor());
            if (!processControlDescriptor.supportsKeepAlive) {
                addAppTemplateProcessControlIssue(issues, appTemplate, "keepAlive");
            }
            if (!processControlDescriptor.supportsAutostart) {
                addAppTemplateProcessControlIssue(issues, appTemplate, "autostart");
            }
        }
        return issues;
    }

    private static void addInstanceTemplateProcessControlIssue(List<ProductValidationIssueApi> issues,
            TemplateApplication templateApp, String variableName, String templateName, String groupName, boolean usesTemplate) {
        if (Boolean.TRUE.equals(templateApp.processControl.get(variableName))) {
            issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                    "Instance template '" + templateName + "' has group '" + groupName + "' which uses "
                            + (usesTemplate ? "template" : "application") + " '"
                            + (usesTemplate ? templateApp.template : templateApp.application) + "' and sets its parameter '"
                            + variableName + "' to enabled, but the descriptor of the application forbids it"));
        }
    }

    private static void addAppTemplateProcessControlIssue(List<ProductValidationIssueApi> issues,
            ApplicationTemplateDescriptor appTemplate, String variableName) {
        if (Boolean.TRUE.equals(appTemplate.processControl.get(variableName))) {
            issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR, "Application template '" + appTemplate.id
                    + "' has '" + variableName + "' enabled, but the descriptor of the application forbids it"));
        }
    }

    private static Collection<? extends ProductValidationIssueApi> validateTemplateVariablesOnInstance(
            InstanceTemplateDescriptor t, ProductValidationConfigDescriptor desc) {
        List<ProductValidationIssueApi> result = new ArrayList<>();

        // validate on instanceVariable values, application names and application startParameters
        TrackingTemplateOverrideResolver res = new TrackingTemplateOverrideResolver(Collections.emptyList());
        for (var v : t.instanceVariables) {
            visitSingleInstanceVariable(v, desc, res, t.instanceVariableDefaults);
        }

        var definitions = desc.instanceVariableDefinitions.stream().flatMap(d -> d.definitions.stream()).toList();
        for (var ivv : t.instanceVariableValues) {
            var def = definitions.stream().filter(ivd -> ivd.id.equals(ivv.id)).findFirst();
            if (def.isPresent()) {
                TemplateHelper.process(ivv.value.getPreRenderable(), res, res::canResolve);
            } else {
                result.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Missing definition for used instance variable value " + ivv.id + " in instance template " + t.name));
            }

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

    private static List<ProductValidationIssueApi> validateFlatInstanceTemplate(FlattenedInstanceTemplateConfiguration tpl,
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

    private static void validateCommand(List<ProductValidationIssueApi> issues, String app, ExecutableDescriptor command,
            List<ParameterTemplateDescriptor> parameterTemplates, boolean requireValue) {

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
                        "Application '" + app + "' has parameters with duplicate id '" + param.id + '\''));
            }
            startIds.add(param.id);

            if (requireValue && (param.defaultValue == null || param.defaultValue.getPreRenderable() == null)) {
                issues.add(new ProductValidationIssueApi(ProductValidationSeverity.ERROR,
                        "Parameter '" + param.id + "' of application '" + app + "' must have a default value"));
            }
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
            return parse(unzipped);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursiveRetry(tmpDir);
        }
    }

    private static ProductValidationConfigDescriptor parse(Path dir) {
        PublicSchemaValidator val = new PublicSchemaValidator();

        ProductValidationDescriptorApi productValidationDescriptorApi = parse(val, dir, ProductValidationDescriptorApi.class,
                Schema.productValidationYaml, ProductValidationDescriptorApi.FILE_NAME);

        ProductValidationConfigDescriptor config = new ProductValidationConfigDescriptor();
        config.product = parse(val, dir, ProductDescriptor.class, Schema.productInfoYaml, productValidationDescriptorApi.product);
        config.applicationTemplates = parse(val, dir, ApplicationTemplateDescriptor.class, Schema.applicationTemplateYaml,
                config.product.applicationTemplates);
        config.instanceTemplates = parse(val, dir, InstanceTemplateDescriptor.class, Schema.instanceTemplateYaml,
                config.product.instanceTemplates);
        config.instanceVariableTemplates = parse(val, dir, InstanceVariableTemplateDescriptor.class,
                Schema.instanceVariableTemplateYaml, config.product.instanceVariableTemplates);
        config.instanceVariableDefinitions = parse(val, dir, InstanceVariableDefinitionDescriptor.class,
                Schema.instanceVariableDefinitionYaml, config.product.instanceVariableDefinitions);
        config.parameterTemplates = parse(val, dir, ParameterTemplateDescriptor.class, Schema.parameterTemplateYaml,
                config.product.parameterTemplates);
        config.applications = productValidationDescriptorApi.applications.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> parse(val, dir, ApplicationDescriptor.class, Schema.appInfoYaml, entry.getValue())));
        return config;
    }

    private static <T> List<T> parse(PublicSchemaValidator val, Path root, Class<T> klass, Schema schema, List<String> files) {
        return files == null ? Collections.emptyList()
                : files.stream().map(file -> parse(val, root, klass, schema, file)).toList();
    }

    private static <T> T parse(PublicSchemaValidator val, Path root, Class<T> klass, Schema schema, String file) {
        Path filePath = root.resolve(file);
        if (schema != null) {
            List<String> result = val.validate(schema, filePath);
            if (!result.isEmpty()) {
                throw new SchemaValidationException(root.relativize(filePath), result);
            }
        }
        try (InputStream is = Files.newInputStream(filePath)) {
            return StorageHelper.fromYamlStream(is, klass);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + root.relativize(filePath), e);
        }
    }

    private static class SchemaValidationException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final transient List<String> errors;
        private final transient Path path;

        private SchemaValidationException(Path path, List<String> errors) {
            this.path = path;
            this.errors = errors;
        }
    }
}
