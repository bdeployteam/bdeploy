package io.bdeploy.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.URLish;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.ParameterCondition.ParameterConditionType;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor.VariableType;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.nodes.NodeType;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathDummyResolver;
import io.bdeploy.interfaces.variables.EmptyVariableResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableDummyResolver;
import io.bdeploy.interfaces.variables.LocalHostnameResolver;
import io.bdeploy.interfaces.variables.ManifestVariableDummyResolver;
import io.bdeploy.interfaces.variables.Resolvers;

@Service
public class ProductUpdateService {

    private static final Logger log = LoggerFactory.getLogger(ProductUpdateService.class);

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
                    "Source product not available, command line cannot be migrated -> please check parameters manually"));
        }

        Map<ApplicationConfiguration, InstanceNodeConfigurationDto> allApps = new HashMap<>();
        for (var node : instance.config.nodeDtos) {
            node.nodeConfiguration.product = targetProduct.getKey();

            for (var app : node.nodeConfiguration.applications) {
                allApps.put(app, node);
            }
        }

        Set<ApplicationConfiguration> apps = allApps.keySet();
        for (var entry : allApps.entrySet()) {
            ApplicationConfiguration app = entry.getKey();
            updateApplication(app, apps, targetProduct, currentApplications, targetApplications, validationIssues,
                    createResolver(entry.getValue(), app));
        }

        if (currentProduct == null) {
            validationIssues.add(0, new ApplicationValidationDto(null, null,
                    "Cannot check for updated configuration files since source product is not avilable. Please check manually."));
        } else if (!Objects.equals(currentProduct.getConfigTemplateTreeId(), targetProduct.getConfigTemplateTreeId())) {
            // there have been changes in the configuration templates of the product
            validationIssues.add(0, new ApplicationValidationDto(null, null,
                    "Product version has updated configuration files. Please make sure to synchronize configuration files."));
        }

        recalculateInstanceVariables(instance, targetProduct, currentProduct);

        return instance;
    }

    private static void recalculateInstanceVariables(InstanceUpdateDto instance, ProductManifest targetProduct,
            ProductManifest currentProduct) {
        Set<String> currentProductVarIds = new HashSet<>();
        if (currentProduct != null) {
            for (VariableDescriptor instVar : currentProduct.getInstanceVariables()) {
                currentProductVarIds.add(instVar.id);
            }
        }

        Set<String> targetProductVarIds = new HashSet<>();
        for (VariableDescriptor instVar : targetProduct.getInstanceVariables()) {
            targetProductVarIds.add(instVar.id);
        }

        List<VariableConfiguration> instanceVariables = new ArrayList<>();
        for (VariableConfiguration instanceVariable : instance.config.config.instanceVariables) {
            String id = instanceVariable.id;
            // remove (skip) all variables defined in current product and not in target product (outdated)
            if (currentProductVarIds.contains(id) && !targetProductVarIds.contains(id)) {
                continue;
            }
            // if existing variable is defined in target product then keep the variable
            // and remove its id from targetProductVarIds so it is not reintroduced and overwritten later
            if (targetProductVarIds.contains(id)) {
                targetProductVarIds.remove(id);
            }
            instanceVariables.add(instanceVariable);
        }

        // add new instance variables defined by target products
        for (VariableDescriptor instVar : targetProduct.getInstanceVariables()) {
            if (targetProductVarIds.contains(instVar.id)) {
                instanceVariables.add(new VariableConfiguration(instVar));
            }
        }

        instance.config.config.instanceVariables = instanceVariables;
    }

    private static void updateApplication(ApplicationConfiguration app, Set<ApplicationConfiguration> allApps,
            ProductManifest targetProduct, List<ApplicationManifest> currentApplications,
            List<ApplicationManifest> targetApplications, List<ApplicationValidationDto> validationIssues,
            VariableResolver resolver) {

        Optional<ApplicationManifest> current = Optional.empty();
        if (currentApplications != null) {
            current = currentApplications.stream().filter(a -> a.getKey().equals(app.application)).findFirst();

            if (current.isEmpty()) {
                throw new IllegalStateException("Cannot find current application: " + app.application);
            }
        }

        var target = targetApplications.stream().filter(a -> a.getKey().getName().equals(app.application.getName())).findFirst();
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
        app.processControl.livenessProbe = targetDesc.processControl.livenessProbe;

        if (app.start != null && app.start.parameters != null && !app.start.parameters.isEmpty()) {
            // update existing parameter order (just the order)
            app.start.parameters = reorderParameters(app.start.parameters, targetDesc.startCommand.parameters);
        }

        if (targetDesc.startCommand.parameters != null && !targetDesc.startCommand.parameters.isEmpty()) {
            app.start.executable = targetDesc.startCommand.launcherPath;

            if (current.isPresent()) {
                // update parameters, add missing, add validation notice for removed parameters
                app.start.parameters = updateParameters(app, targetDesc, app.start.parameters, targetDesc.startCommand.parameters,
                        current.get().getDescriptor().startCommand.parameters, allApps, validationIssues, resolver);
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
            app.stop = createCommand(targetDesc.stopCommand, targetDesc, allApps, resolver);
        }
    }

    private static List<ParameterConfiguration> reorderParameters(List<ParameterConfiguration> values,
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

    private static List<HttpEndpoint> updateEndpoints(List<HttpEndpoint> values, List<HttpEndpoint> descriptors) {
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

    private static List<ParameterConfiguration> updateParameters(ApplicationConfiguration app, ApplicationDescriptor appDesc,
            List<ParameterConfiguration> values, List<ParameterDescriptor> descriptors, List<ParameterDescriptor> oldDescriptors,
            Set<ApplicationConfiguration> allApps, List<ApplicationValidationDto> validation, VariableResolver resolver) {

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
                                + " -> resetting to default value for '" + desc.get().name + '\''));
            } else if (oldDesc.isPresent() && desc.isEmpty()) {
                // previously defined parameter is now undefined, we want to
                toReset.put(val, null);
                validation.add(new ApplicationValidationDto(app.id, val.id, "Parameter has been removed."));
            }

            // 3) update the value of fixed parameters - if global fetch value from an existing global parameter.
            if (desc.isPresent() && desc.get().fixed && desc.get().defaultValue != null) {
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
                createParameter(entry.getValue(), descriptors, values, allApps);
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
                createParameter(desc, descriptors, values, allApps);

                if (desc.global
                        && validation.stream().filter(v -> v.appId == null && desc.id.equals(v.paramId)).findFirst().isEmpty()) {
                    validation.add(0, new ApplicationValidationDto(null, desc.id,
                            "New global parameter '" + desc.name + "' has been added with its default value."));
                }
            }
        }

        return values;
    }

    private static void createParameter(ParameterDescriptor desc, List<ParameterDescriptor> allDescs,
            List<ParameterConfiguration> values, Set<ApplicationConfiguration> allApps) {
        ParameterConfiguration cfg = new ParameterConfiguration();
        cfg.id = desc.id;
        cfg.value = desc.defaultValue;

        if (desc.global) {
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

    private static Optional<ParameterConfiguration> getParameter(ApplicationConfiguration config, String id) {
        Optional<ParameterConfiguration> para = Optional.empty();

        if (config != null && config.start != null && config.start.parameters != null) {
            para = config.start.parameters.stream().filter(p -> p.id.equals(id)).findFirst();
        }

        if (para.isEmpty() && config != null && config.stop != null && config.stop.parameters != null) {
            para = config.stop.parameters.stream().filter(p -> p.id.equals(id)).findFirst();
        }

        return para;
    }

    private static CommandConfiguration createCommand(ExecutableDescriptor desc, ApplicationDescriptor appDesc,
            Set<ApplicationConfiguration> allApps, VariableResolver resolver) {
        CommandConfiguration result = new CommandConfiguration();

        result.executable = desc.launcherPath;

        for (var para : desc.parameters) {
            // same as in the TS code, this assumes that the parameter referenced in the condition is *before* the conditional.
            if (para.mandatory && meetsCondition(appDesc, para, resolver)) {
                createParameter(para, desc.parameters, result.parameters, allApps);
            }
        }

        return result;
    }

    public List<ApplicationValidationDto> validate(InstanceUpdateDto updateDto, Collection<ApplicationManifest> applications,
            SystemConfiguration system, Collection<FileStatusDto> existingConfigFiles) {
        List<ApplicationValidationDto> result = new ArrayList<>();
        InstanceConfigurationDto instance = updateDto.config;
        List<InstanceNodeConfigurationDto> nodes = instance.nodeDtos;

        for (var node : nodes) {
            // update variables in case we modified them in the instance config.
            node.nodeConfiguration.mergeVariables(instance.config, system, null);
        }

        // Validate configuration files
        validateFiles(result, nodes, updateDto.files);

        // TODO: this reverts CT_BDEPLOY-342. Un-revert after we made changes to whitelisting/blacklisting config files!
        //        if (existingConfigFiles != null && !existingConfigFiles.isEmpty()) {
        //            Collection<FileStatusDto> existingUnchanged = updateDto.files == null
        //                    ? existingConfigFiles
        //                    : existingConfigFiles.stream().filter(f -> !isFileChanged(updateDto.files, f.file)).toList();
        //
        //            validateFiles(result, nodes, existingUnchanged);
        //        }

        // Validate applications and processes
        Map<String, String> processNames = new TreeMap<>();
        for (var node : nodes) {
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
                if (conflictUid != null && node.nodeConfiguration.nodeType != NodeType.CLIENT) {
                    result.add(new ApplicationValidationDto(process.id, null,
                            "The process name " + process.name + " is not unique."));
                    result.add(new ApplicationValidationDto(conflictUid, null,
                            "The process name " + process.name + " is not unique."));
                }

                VariableResolver res = createResolver(node, process);

                // check all parameters
                result.addAll(validateCommand(process, desc.get(), process.start, desc.get().startCommand, res));
                result.addAll(validateCommand(process, desc.get(), process.stop, desc.get().stopCommand, res));
            }
        }

        return result;
    }

    @SuppressWarnings("unused") // TODO Will be used in the future
    private static boolean isFileChanged(List<FileStatusDto> fileStatuses, String fileName) {
        return fileStatuses.stream().anyMatch(fileStatus -> fileStatus.file.equals(fileName));
    }

    private static void validateFiles(List<ApplicationValidationDto> result, List<InstanceNodeConfigurationDto> nodes,
            Collection<FileStatusDto> files) {
        if (files == null) {
            return;
        }
        List<FileStatusDto> toCheck = files.stream().filter(file -> file.type != FileStatusType.DELETE).toList();
        if (toCheck.isEmpty()) {
            return;
        }
        for (var node : nodes) {
            String nodeName = node.nodeName;
            VariableResolver resolver = createResolver(node, null);
            if (node.nodeConfiguration.nodeType == NodeType.CLIENT) {
                node.nodeConfiguration.applications.stream()//
                        .map(app -> app.processControl.configDirs)//
                        .filter(d -> d != null && !d.isEmpty())//
                        .forEach(dirsString -> {
                            String[] split = ProcessControlConfiguration.CONFIG_DIRS_SPLIT_PATTERN.split(dirsString);
                            Set<String> configDirs = Arrays.stream(split).map(s -> s.startsWith("/") ? s.substring(1) : s)
                                    .collect(Collectors.toSet());
                            toCheck.stream()//
                                    .filter(file -> {
                                        var p = Path.of(file.file).getParent();
                                        return configDirs.contains(p != null ? p.toString() : "");
                                    })//
                                    .forEach(file -> validateFile(result, resolver, InstanceManifest.CLIENT_NODE_LABEL, file));
                        });
            } else {
                toCheck.forEach(file -> validateFile(result, resolver, nodeName, file));
            }
        }
    }

    private static void validateFile(List<ApplicationValidationDto> result, VariableResolver resolver, String nodeName,
            FileStatusDto file) {
        String filePath = file.file;
        try {
            String content = new String(Base64.decodeBase64(file.content), StandardCharsets.UTF_8);
            TemplateHelper.process(content, resolver, str -> Boolean.TRUE, filePath);
        } catch (Exception e) {
            result.add(new ApplicationValidationDto(filePath, null, e.getMessage() + " on node " + nodeName));
        }
    }

    public static VariableResolver createResolver(InstanceNodeConfigurationDto node, ApplicationConfiguration process) {
        CompositeResolver res = Resolvers.forInstance(node.nodeConfiguration, "1", null);
        res.add(new ManifestVariableDummyResolver());
        res.add(new DeploymentPathDummyResolver());
        res.add(new EnvironmentVariableDummyResolver());
        res.add(new LocalHostnameResolver(false));
        if (process != null) {
            return Resolvers.forApplication(res, node.nodeConfiguration, process);
        }
        return res;
    }

    private static List<ApplicationValidationDto> validateCommand(ApplicationConfiguration process, ApplicationDescriptor appDesc,
            CommandConfiguration command, ExecutableDescriptor desc, VariableResolver resolver) {
        List<ApplicationValidationDto> result = new ArrayList<>();

        if (command == null || desc == null) {
            return result;
        }

        if (command.executable == null || !command.executable.equals(desc.launcherPath)) {
            result.add(new ApplicationValidationDto(process.id, null,
                    "Assigned Exectuable does not match the required launcher path"));
        }

        for (var paramDesc : desc.parameters) {
            var value = command.parameters.stream().filter(p -> p.id.equals(paramDesc.id)).findFirst().orElse(null);

            validateParameter(process, appDesc, value, paramDesc, result, resolver);
        }

        return result;
    }

    private static void validateParameter(ApplicationConfiguration process, ApplicationDescriptor appDesc,
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
            result.add(new ApplicationValidationDto(process.id, paramDesc.id, "Mandatory parameter has no value"));
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
            // some expansion was not good
            result.add(
                    new ApplicationValidationDto(process.id, paramDesc.id, "Cannot resolve link expressions: " + e.getMessage()));
            return;
        }

        // check allowed values per type.
        switch (paramDesc.type) {
            case STRING, PASSWORD, ENVIRONMENT:
                // No validation for these types
                break;
            case BOOLEAN:
                if (!"true".equals(stringVal) && !"false".equals(stringVal)) {
                    result.add(new ApplicationValidationDto(process.id, paramDesc.id,
                            "Boolean parameter should have value 'true' or 'false', has '" + stringVal + "' instead."));
                }
                break;
            case CLIENT_PORT, SERVER_PORT, NUMERIC:
                try {
                    long l = Long.parseLong(stringVal);
                    if (paramDesc.type != VariableType.NUMERIC && (l < 0 || l > (Short.MAX_VALUE * 2))) {
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
        }
    }

    public static boolean meetsCondition(ApplicationDescriptor desc, ParameterDescriptor param, VariableResolver resolver) {
        if (param.condition == null || (param.condition.parameter == null && param.condition.expression == null)) {
            return true;
        }

        String value = null;
        String expression = param.condition.expression;

        // we need a "target" type. in case of expression, this is our own type, in case of "parameter" it is the type of the target parameter
        VariableType targetType = param.type;
        if (param.condition.parameter != null) {
            expression = "{{V:" + param.condition.parameter + "}}"; // compat with older model.

            // find a descriptor and it's type if possible, fall back to parameters own type.
            targetType = desc.startCommand.parameters.stream().filter(d -> d.id.equals(param.condition.parameter)).findFirst()
                    .map(p -> p.type).orElse(param.type);
        }

        try {
            // we need a resolver which resolves to empty string as last resort.
            // in case a value is requested using BE_EMPTY, non-present parameters/variables
            // should not cause failure.
            CompositeResolver comp = new CompositeResolver();
            comp.add(resolver);
            comp.add(new EmptyVariableResolver());

            value = TemplateHelper.process(expression, comp);
        } catch (Exception e) {
            // that does not resolve, so it is not good :)
            if (log.isTraceEnabled()) {
                log.trace("Failed to resolve expression: {}", expression, e);
            }
            return false;
        }

        if (value == null) {
            return param.condition.must == ParameterConditionType.BE_EMPTY;
        }

        return switch (param.condition.must) {
            case BE_EMPTY -> value.isBlank() || (targetType == VariableType.BOOLEAN && "false".equals(value.trim()));
            case BE_NON_EMPTY -> !value.isBlank() && !(targetType == VariableType.BOOLEAN && "false".equals(value.trim()));
            case CONTAIN -> value.contains(param.condition.value);
            case END_WITH -> value.endsWith(param.condition.value);
            case EQUAL -> value.equals(param.condition.value);
            case START_WITH -> value.startsWith(param.condition.value);
            default -> true;
        };
    }
}
