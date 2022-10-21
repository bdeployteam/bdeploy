package io.bdeploy.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.URLish;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.ParameterCondition.ParameterConditionType;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.ConditionalExpressionResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathValidationDummyResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableDummyResolver;
import io.bdeploy.interfaces.variables.InstanceAndSystemVariableResolver;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.LocalHostnameResolver;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;
import io.bdeploy.interfaces.variables.ManifestVariableValidationDummyResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;

@Service
public class ProductUpdateService {

    public InstanceUpdateDto update(InstanceUpdateDto instance, ProductManifest targetProduct, ProductManifest currentProduct,
            List<ApplicationManifest> targetApplications, List<ApplicationManifest> currentApplications) {
        List<ApplicationValidationDto> validationIssues = new ArrayList<>();
        instance.validation = validationIssues;

        instance.config.config.product = targetProduct.getKey();

        if (instance.config.config.configTree == null) {
            instance.config.config.configTree = targetProduct.getConfigTemplateTreeId();
        }

        if (currentApplications == null) {
            validationIssues.add(new ApplicationValidationDto(null, null,
                    "Source product not available, command line cannot be migrated. Please check parameters manually."));
        }

        Map<ApplicationConfiguration, InstanceNodeConfigurationDto> allApps = new HashMap<>();
        for (var node : instance.config.nodeDtos) {
            node.nodeConfiguration.product = targetProduct.getKey();

            for (var app : node.nodeConfiguration.applications) {
                allApps.put(app, node);
            }
        }

        for (var app : allApps.keySet()) {
            var node = allApps.get(app);

            updateApplication(instance.config.config, app, allApps.keySet(), targetProduct, currentApplications,
                    targetApplications, validationIssues, createResolver(node, app));
        }

        if (currentProduct == null) {
            validationIssues.add(0, new ApplicationValidationDto(null, null,
                    "Cannot check for updated configuration files since source product is not avilable. Please check manually."));
        } else if (!Objects.equals(currentProduct.getConfigTemplateTreeId(), targetProduct.getConfigTemplateTreeId())) {
            // there have been changes in the configuration templates of the product
            validationIssues.add(0, new ApplicationValidationDto(null, null,
                    "Product version has updated configuration files. Please make sure to synchronize configuration files."));
        }

        return instance;
    }

    private void updateApplication(InstanceConfiguration instance, ApplicationConfiguration app,
            Set<ApplicationConfiguration> allApps, ProductManifest targetProduct, List<ApplicationManifest> currentApplications,
            List<ApplicationManifest> targetApplications, List<ApplicationValidationDto> validationIssues,
            VariableResolver resolver) {
        var current = currentApplications == null ? null
                : currentApplications.stream().filter(a -> a.getKey().equals(app.application)).findFirst();
        var target = targetApplications.stream().filter(a -> a.getKey().getName().equals(app.application.getName())).findFirst();

        if (current != null && current.isEmpty()) {
            throw new IllegalStateException("Cannot find current application: " + app.application);
        }

        if (target.isEmpty()) {
            // cannot update, application no longer exists. perform "dummy" update, so validation detects this.
            app.application = new Manifest.Key(app.application.getName(), "NOT_PRESENT");
            validationIssues.add(new ApplicationValidationDto(app.id, null, "Application " + app.application.getName()
                    + " not available in product version " + targetProduct.getKey().getTag()));
            return;
        }

        ApplicationDescriptor targetDesc = target.get().getDescriptor();
        app.application = target.get().getKey();
        app.pooling = targetDesc.pooling;

        // update process control data - this is not configurable by user.
        app.processControl.startupProbe = targetDesc.processControl.startupProbe;
        app.processControl.lifenessProbe = targetDesc.processControl.lifenessProbe;

        if (app.start != null && app.start.parameters != null && !app.start.parameters.isEmpty()) {
            // update existing parameter order (just the order)
            app.start.parameters = reorderParameters(app.start.parameters, targetDesc.startCommand.parameters);
        }

        if (targetDesc.startCommand.parameters != null && !targetDesc.startCommand.parameters.isEmpty()) {
            app.start.executable = targetDesc.startCommand.launcherPath;

            if (current != null) {
                // update parameters, add missing, add validation notice for removed parameters
                app.start.parameters = updateParameters(instance, app, targetDesc, app.start.parameters,
                        targetDesc.startCommand.parameters, current.get().getDescriptor().startCommand.parameters, allApps,
                        validationIssues, resolver);
            }
        }

        List<HttpEndpoint> epDescs = targetDesc.endpoints == null || targetDesc.endpoints.http == null ? Collections.emptyList()
                : targetDesc.endpoints.http;
        List<HttpEndpoint> epValues = app.endpoints == null || app.endpoints.http == null ? Collections.emptyList()
                : app.endpoints.http;

        // add/remove endpoints.
        app.endpoints.http = updateEndpoints(epValues, epDescs);

        if (targetDesc.stopCommand == null) {
            app.stop = null;
        } else {
            app.stop = createCommand(instance, targetDesc.stopCommand, targetDesc, allApps, resolver);
        }
    }

    private List<ParameterConfiguration> reorderParameters(List<ParameterConfiguration> values,
            List<ParameterDescriptor> descriptors) {
        List<ParameterConfiguration> reordered = new ArrayList<>();

        // the descriptors dictate the order.
        for (var desc : descriptors) {
            var val = values.stream().filter(p -> p.id.equals(desc.id)).findFirst();
            if (val.isPresent()) {
                // we have a value, so put it next.
                reordered.add(val.get());
            }
        }

        // for each value which is not yet in the list, find the predecessor, and add it at the new location.
        for (var val : values) {
            if (reordered.contains(val)) {
                continue;
            }

            int index = values.indexOf(val);
            if (index == 0) {
                // no predecessor, add at the beginning.
                reordered.add(0, val);
                continue;
            }

            // find the original predecessor
            var predecessor = values.get(index - 1);
            var predIndex = reordered.indexOf(predecessor);

            // add after the *new* location of the predecessor.
            reordered.add(predIndex + 1, val);
        }

        return reordered;
    }

    private List<HttpEndpoint> updateEndpoints(List<HttpEndpoint> values, List<HttpEndpoint> descriptors) {
        List<HttpEndpoint> result = new ArrayList<>();

        // each descriptor must be present, prefer existing config, otherwise add default.
        // each value where no descriptor exists is implicitly dropped.
        for (var desc : descriptors) {
            var val = values.stream().filter(e -> e.id.equals(desc.id)).findFirst();
            if (val.isPresent()) {
                HttpEndpoint ep = val.get();
                ep.path = desc.path;
                ep.type = desc.type;
                ep.proxying = desc.proxying;
                result.add(ep);
            } else {
                result.add(desc);
            }
        }

        return result;
    }

    private List<ParameterConfiguration> updateParameters(InstanceConfiguration instance, ApplicationConfiguration app,
            ApplicationDescriptor appDesc, List<ParameterConfiguration> values, List<ParameterDescriptor> descriptors,
            List<ParameterDescriptor> oldDescriptors, Set<ApplicationConfiguration> allApps,
            List<ApplicationValidationDto> validation, VariableResolver resolver) {

        // 1) find parameters which have a value but are no longer in the descriptor, remove them, issue validation warning.
        Map<ParameterConfiguration, ParameterDescriptor> toReset = new HashMap<>();
        for (var val : values) {
            var oldDesc = oldDescriptors.stream().filter(p -> p.id.equals(val.id)).findFirst();
            var desc = descriptors.stream().filter(p -> p.id.equals(val.id)).findFirst();

            if (oldDesc.isEmpty() && desc.isPresent()) {
                // previously "custom" parameter now collides with a newly added one. need to re-create the parameter.
                toReset.put(val, desc.get());
                validation.add(new ApplicationValidationDto(app.id, val.id,
                        "Previously custom parameter's id collides with newly added parameter: " + val.id
                                + ". Resetting to default value for '" + desc.get().name + "'."));
            } else if (oldDesc.isPresent() && desc.isEmpty()) {
                // previously defined parameter is now undefined, we want to
                toReset.put(val, null);
                validation.add(new ApplicationValidationDto(app.id, val.id, "Parameter has been removed."));
            }

            // 3) update the value of fixed parameters - if global fetch value from an existing global parameter.
            if (desc.isPresent() && desc.get().fixed) {
                val.value = desc.get().defaultValue;
            }

            // 4) ALWAYS pre-render the parameter to update if the descriptor's contents has changed
            if (desc.isPresent()) {
                val.preRender(desc.get());
            }
        }

        for (var entry : toReset.entrySet()) {
            values.remove(entry.getKey());
            if (entry.getValue() != null && meetsCondition(appDesc, entry.getValue(), resolver)) {
                createParameter(instance, entry.getValue(), descriptors, values, allApps);
            }
        }

        // 2) find parameters which are mandatory but not yet present and add *before* the succeeding parameter desc (avoid ordering issues with custom parameters).
        for (var desc : descriptors) {
            if (!desc.mandatory) {
                continue; // don't care :)
            }

            var val = values.stream().filter(p -> p.id.equals(desc.id)).findFirst();
            if (val.isEmpty() && meetsCondition(appDesc, desc, resolver)) {
                // need one.
                createParameter(instance, desc, descriptors, values, allApps);

                if (desc.global && !instance.globalsMigrated) {
                    if (validation.stream().filter(v -> v.appId == null && desc.id.equals(v.paramId)).findFirst().isEmpty()) {
                        validation.add(0, new ApplicationValidationDto(null, desc.id,
                                "New global parameter '" + desc.name + "' has been added with its default value."));
                    }
                } else {
                    validation.add(new ApplicationValidationDto(app.id, desc.id,
                            "New mandatory parameter '" + desc.name + "' has been added with its default value."));
                }
            }
        }

        return values;
    }

    private void createParameter(InstanceConfiguration instance, ParameterDescriptor desc, List<ParameterDescriptor> allDescs,
            List<ParameterConfiguration> values, Set<ApplicationConfiguration> allApps) {
        ParameterConfiguration cfg = new ParameterConfiguration();
        cfg.id = desc.id;
        cfg.value = desc.defaultValue;

        if (desc.global && !instance.globalsMigrated) {
            for (var other : allApps) {
                var para = getParameter(other, desc.id);
                if (para.isPresent()) {
                    cfg.value = para.get().value;
                    break;
                }
            }
        }

        cfg.preRender(desc);

        // find the first successor descriptor which has a value. we want to add *before* that to keep
        // custom parameter order intact.
        Optional<ParameterConfiguration> successor = Optional.empty();
        for (int i = allDescs.indexOf(desc); i < allDescs.size(); ++i) {
            var possibleSuccessor = allDescs.get(i);
            successor = values.stream().filter(p -> p.id.equals(possibleSuccessor.id)).findFirst();

            if (successor.isPresent()) {
                break;
            }
        }

        if (successor.isEmpty()) {
            values.add(cfg); // no successor with value, add at the end.
        } else {
            values.add(values.indexOf(successor.get()), cfg);
        }
    }

    private Optional<ParameterConfiguration> getParameter(ApplicationConfiguration config, String id) {
        Optional<ParameterConfiguration> para = Optional.empty();

        if (config != null && config.start != null && config.start.parameters != null) {
            para = config.start.parameters.stream().filter(p -> p.id.equals(id)).findFirst();
        }

        if (para.isEmpty() && config != null && config.stop != null && config.stop.parameters != null) {
            para = config.stop.parameters.stream().filter(p -> p.id.equals(id)).findFirst();
        }

        return para;
    }

    private CommandConfiguration createCommand(InstanceConfiguration instance, ExecutableDescriptor desc,
            ApplicationDescriptor appDesc, Set<ApplicationConfiguration> allApps, VariableResolver resolver) {
        CommandConfiguration result = new CommandConfiguration();

        result.executable = desc.launcherPath;

        for (var para : desc.parameters) {
            // same as in the TS code, this assumes that the parameter referenced in the condition is *before* the conditional.
            if (para.mandatory && meetsCondition(appDesc, para, resolver)) {
                createParameter(instance, para, desc.parameters, result.parameters, allApps);
            }
        }

        return result;
    }

    public List<ApplicationValidationDto> validate(InstanceUpdateDto instance, List<ApplicationManifest> applications,
            SystemConfiguration system) {
        List<ApplicationValidationDto> result = new ArrayList<>();

        // there is nothing in the base config which requires excessive validation right now. mandatory fields are
        // validated in the client(s) individually.

        Map<String, String> processNames = new TreeMap<>();

        for (var node : instance.config.nodeDtos) {
            // again. the node configuration itself does not carry much information which needs validation.
            for (var process : node.nodeConfiguration.applications) {
                var desc = applications.stream().filter(m -> m.getKey().getName().equals(process.application.getName()))
                        .map(ApplicationManifest::getDescriptor).findFirst();
                if (desc.isEmpty()) {
                    result.add(new ApplicationValidationDto(process.id, null,
                            "Cannot find application " + process.application.getName()));
                    continue;
                }

                // check unique process names
                var conflictUid = processNames.put(process.name, process.id);
                if (conflictUid != null && !node.nodeName.equals(InstanceManifest.CLIENT_NODE_NAME)) {
                    result.add(new ApplicationValidationDto(process.id, null,
                            "The process name " + process.name + " is not unique."));
                    result.add(new ApplicationValidationDto(conflictUid, null,
                            "The process name " + process.name + " is not unique."));
                }

                // update variables in case we modified them in the instance config.
                node.nodeConfiguration.mergeVariables(instance.config.config, system, null);

                VariableResolver res = createResolver(node, process);

                // check all parameters
                result.addAll(validateCommand(process, desc.get(), process.start, desc.get().startCommand, res));
                result.addAll(validateCommand(process, desc.get(), process.stop, desc.get().stopCommand, res));
            }
        }

        return result;
    }

    public static VariableResolver createResolver(InstanceNodeConfigurationDto node, ApplicationConfiguration process) {
        CompositeResolver res = new CompositeResolver();
        res.add(new InstanceAndSystemVariableResolver(node.nodeConfiguration));
        res.add(new ConditionalExpressionResolver(res));
        res.add(new InstanceVariableResolver(node.nodeConfiguration, null, "1"));
        res.add(new ApplicationVariableResolver(process));
        res.add(new ApplicationParameterValueResolver(process.id, node.nodeConfiguration));
        ManifestVariableValidationDummyResolver dummy = new ManifestVariableValidationDummyResolver();
        res.add(new ManifestSelfResolver(process.application, dummy));
        res.add(new DeploymentPathValidationDummyResolver());
        res.add(dummy);
        res.add(new ParameterValueResolver(new ApplicationParameterProvider(node.nodeConfiguration)));
        res.add(new OsVariableResolver());
        res.add(new LocalHostnameResolver(false));
        res.add(new EnvironmentVariableDummyResolver());
        res.add(new DelayedVariableResolver(res));

        return res;
    }

    private List<ApplicationValidationDto> validateCommand(ApplicationConfiguration process, ApplicationDescriptor appDesc,
            CommandConfiguration command, ExecutableDescriptor desc, VariableResolver resolver) {
        List<ApplicationValidationDto> result = new ArrayList<>();

        if (command == null || desc == null) {
            return result;
        }

        if (command.executable == null || !command.executable.equals(desc.launcherPath)) {
            result.add(new ApplicationValidationDto(process.id, null,
                    "Assigned Exectuable does not match the required launcher path."));
        }

        for (var paramDesc : desc.parameters) {
            var value = command.parameters.stream().filter(p -> p.id.equals(paramDesc.id)).findFirst().orElse(null);

            validateParameter(process, appDesc, value, paramDesc, result, resolver);
        }

        return result;
    }

    private void validateParameter(ApplicationConfiguration process, ApplicationDescriptor appDesc,
            ParameterConfiguration paramValue, ParameterDescriptor paramDesc, List<ApplicationValidationDto> result,
            VariableResolver resolver) {
        // check condition.
        if (!meetsCondition(appDesc, paramDesc, resolver)) {
            if (paramValue != null && paramValue.value != null) {
                result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Parameter does not meet required condition"));
            }
            return;
        }

        // check mandatory.
        if (paramDesc.mandatory && (paramValue == null || paramValue.value == null)) {
            result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Mandatory parameter has no value."));
        }

        if (paramValue == null || paramValue.value == null) {
            return;
        }

        var rawExpr = paramValue.value.linkExpression;

        // check syntax of variable substitutions.
        if (rawExpr != null && (rawExpr.contains("{{") || rawExpr.contains("}}"))
                && (!rawExpr.contains("{{") || !rawExpr.contains("}}") || !rawExpr.contains(":"))) {
            result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Invalid variable substitution syntax"));
        }

        String stringVal;
        try {
            stringVal = rawExpr == null ? paramValue.value.value : TemplateHelper.process(rawExpr, resolver);
        } catch (IllegalArgumentException e) {
            // some expansion was not good;
            result.add(
                    new ApplicationValidationDto(process.id, paramDesc.id, "Cannot resolve link expressions: " + e.getMessage()));
            return;
        }

        // check allowed values per type.
        switch (paramDesc.type) {
            case BOOLEAN:
                if (!stringVal.equals("true") && !stringVal.equals("false")) {
                    result.add(new ApplicationValidationDto(process.id, paramDesc.id,
                            "Boolean parameter should have value 'true' or 'false', has '" + stringVal + "' instead."));
                }
                break;
            case CLIENT_PORT, SERVER_PORT, NUMERIC:
                try {
                    long l = Long.parseLong(stringVal);
                    if (paramDesc.type != ParameterType.NUMERIC && (l < 0 || l > (Short.MAX_VALUE * 2))) {
                        result.add(new ApplicationValidationDto(process.id, paramDesc.id,
                                "Value for port parameter is out of range: " + l));
                    }
                } catch (NumberFormatException e) {
                    result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Value must be numeric, is: " + stringVal));
                }
                break;
            case URL:
                try {
                    new URLish(stringVal);
                } catch (IllegalArgumentException e) {
                    result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Value must be URL-like."));
                }
                break;
            default:
                break;
        }
    }

    public static boolean meetsCondition(ApplicationDescriptor desc, ParameterDescriptor param, VariableResolver resolver) {
        if (param.condition == null || (param.condition.parameter == null && param.condition.expression == null)) {
            return true;
        }

        String value = null;
        String expression = param.condition.expression;

        // we need a "target" type. in case of expression, this is our own type, in case of "parameter" it is the type of the target parameter
        ParameterType targetType = param.type;
        if (param.condition.parameter != null) {
            expression = "{{V:" + param.condition.parameter + "}}"; // compat with older model.

            // find a descriptor and it's type if possible, fall back to parameters own type.
            targetType = desc.startCommand.parameters.stream().filter(d -> d.id.equals(param.condition.parameter)).findFirst()
                    .map(p -> p.type).orElse(param.type);
        }

        try {
            value = TemplateHelper.process(expression, resolver);
        } catch (Exception e) {
            // that does not resolve, so it is not good :)
            return false;
        }

        if (value == null) {
            return param.condition.must == ParameterConditionType.BE_EMPTY;
        }

        switch (param.condition.must) {
            case BE_EMPTY:
                return value.isBlank() || (targetType == ParameterType.BOOLEAN && value.trim().equals("false"));
            case BE_NON_EMPTY:
                return !value.isBlank() && !(targetType == ParameterType.BOOLEAN && value.trim().equals("false"));
            case CONTAIN:
                return value.contains(param.condition.value);
            case END_WITH:
                return value.endsWith(param.condition.value);
            case EQUAL:
                return value.equals(param.condition.value);
            case START_WITH:
                return value.startsWith(param.condition.value);
        }

        return true;
    }

}
