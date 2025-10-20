package io.bdeploy.ui.api.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.poi.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateGroupConfiguration;
import io.bdeploy.interfaces.configuration.template.TemplateVariableResolver;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.nodes.NodeType;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.FixedParameterListValueResolver;
import io.bdeploy.interfaces.variables.Variables;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.InstanceTemplateResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.LatestProductVersionRequestDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductKeyWithSourceDto;
import io.bdeploy.ui.utils.InstanceTemplateHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class InstanceTemplateResourceImpl implements InstanceTemplateResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceTemplateResourceImpl.class);

    private final String group;
    private final BHive hive;

    @Context
    private SecurityContext context;

    @Inject
    private ProductUpdateService pus;

    @Inject
    private MasterProvider mp;

    @Inject
    private ResourceContext rc;

    public InstanceTemplateResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public InstanceTemplateReferenceResultDto updateWithTemplate(InstanceTemplateReferenceDescriptor responseFile, String server,
            String uuid) {
        // sanitize input
        sanitizeInstanceTemplateReferenceDescriptor(responseFile);

        // find instance and load product
        InstanceGroupResource igr = rc.getResource(InstanceGroupResourceImpl.class);
        InstanceResource ir = igr.getInstanceResource(group);
        InstanceDto instanceDto = ir.read(uuid);
        ProductDto productDto = findOrImportProduct(group, responseFile,
                Optional.ofNullable(instanceDto.activeProduct).map(Key::getName).orElse(null));

        // find and verify all group mappings and whether all variables are set for each required group
        FlattenedInstanceTemplateConfiguration tpl = getInstanceTemplateConfig(productDto.instanceTemplates,
                responseFile.templateName, "product version " + productDto.key.getTag() + " of instance " + uuid);

        // create the group to node mapping
        Map<String, String> groupToNode = checkVariablesAndCreateGroupToNodeMapping(responseFile, tpl.directlyUsedTemplateVars,
                tpl.groups);

        // update the instance
        InstanceTemplateReferenceResultDto result = updateInstanceWithTemplateRequest(mp.getNamedMasterOrSelf(hive, server),
                responseFile, ir, instanceDto, tpl, groupToNode, uuid, ProductManifest.of(hive, productDto.key));

        // sync in case of success
        if (result.status != InstanceTemplateReferenceStatus.ERROR) {
            rc.initResource(new ManagedServersResourceImpl()).synchronize(group, server);
        }

        return result;
    }

    private InstanceTemplateReferenceResultDto updateInstanceWithTemplateRequest(RemoteService remote,
            InstanceTemplateReferenceDescriptor responseFile, InstanceResource ir, InstanceDto instanceDto,
            FlattenedInstanceTemplateConfiguration tpl, Map<String, String> groupToNode, String uuid, ProductManifest pmf) {
        InstanceConfiguration cfg = instanceDto.instanceConfiguration;
        TemplateVariableResolver tvr = new TemplateVariableResolver(tpl.directlyUsedTemplateVars, responseFile.fixedVariables,
                null);
        cfg.instanceVariables.addAll(createInstanceVariablesFromTemplate(tpl, tvr, pmf));

        var nodeStates = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNodes();

        // Apply existing nodes
        Map<String, InstanceNodeConfigurationDto> nodes = new TreeMap<>();
        for (var entry : InstanceManifest.load(hive, uuid, null).getInstanceNodeConfigurations(hive).entrySet()) {
            InstanceNodeConfigurationDto instanceNodeConfigurationDto = new InstanceNodeConfigurationDto(entry.getKey(),
                    entry.getValue());
            nodes.put(instanceNodeConfigurationDto.nodeName, instanceNodeConfigurationDto);
        }

        // Add new applications
        List<ApplicationManifest> apps = getApps(pmf);
        try {
            for (InstanceNodeConfigurationDto instanceNodeConfigDto : createInstanceNodesFromTemplate(cfg, tpl, groupToNode, apps,
                    tvr, nodeStates, null, pmf.getProduct())) {
                String nodeName = instanceNodeConfigDto.nodeName;
                nodes.putIfAbsent(nodeName, instanceNodeConfigDto);
                nodes.get(nodeName).nodeConfiguration.applications.addAll(instanceNodeConfigDto.nodeConfiguration.applications);
            }
        } catch (RuntimeException e) {
            log.warn("Exception while updating instance {} with instance template {}", cfg.id, responseFile.templateName, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Failed to update with template: " + e.toString());
        }

        // Create update from the current instance config template tree, this is the starting point.
        // We cannot simply set the tree ID, since the product on the target may not be available (yet).
        List<FileStatusDto> cfgFiles = InstanceResourceImpl.getUpdatesFromTree(hive, "", new ArrayList<>(),
                instanceDto.instanceConfiguration.configTree);
        InstanceUpdateDto updateDto = new InstanceUpdateDto(new InstanceConfigurationDto(cfg, new ArrayList<>(nodes.values())),
                null);

        try {
            List<ApplicationValidationDto> validationDtos = pus.validate(updateDto, apps, null, cfgFiles);
            if (!validationDtos.isEmpty()) {
                validationDtos.forEach(v -> log.warn("Validation problem in instance: {}, app: {}, param: {}: {}", cfg.id,
                        v.appId, v.paramId, v.message));
                ApplicationValidationDto firstMessage = validationDtos.getFirst();
                return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                        "Failed to validate instance " + cfg.id + ", first message: " + firstMessage.message + " ("
                                + firstMessage.appId + ", " + firstMessage.paramId + ")");
            }
        } catch (RuntimeException e) {
            log.warn("Exception while validating instance {}", cfg.id, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Failed to validate instance " + cfg.id + ' ' + e.toString());
        }

        try {
            ir.update(cfg.id, updateDto, null, instanceDto.instance.getTag());
        } catch (RuntimeException e) {
            log.warn("Failed to update instance {}", cfg.id, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Failed to update instance " + cfg.id + ' ' + e.toString());
        }

        return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.OK,
                "Successfully updated instance " + cfg.id);
    }

    @Override
    public InstanceTemplateReferenceResultDto createFromTemplate(InstanceTemplateReferenceDescriptor responseFile,
            InstancePurpose purpose, String server, String system) {
        // sanitize input
        sanitizeInstanceTemplateReferenceDescriptor(responseFile);

        // find, import and verify product
        ProductDto productDto = findOrImportProduct(group, responseFile, null);

        // find and verify all group mappings and whether all variables are set for each required group
        FlattenedInstanceTemplateConfiguration tpl = getInstanceTemplateConfig(productDto.instanceTemplates,
                responseFile.templateName, "best matching product version " + productDto.key);

        // create the group to node mapping
        Map<String, String> groupToNode = checkVariablesAndCreateGroupToNodeMapping(responseFile, tpl.directlyUsedTemplateVars,
                tpl.groups);

        // figure out target system if given
        Optional<Manifest.Key> systemKey = Optional.empty();
        if (system != null) {
            SystemResource sr = rc.initResource(new SystemResourceImpl(group, hive));
            systemKey = sr.list().stream().filter(s -> s.config.id.equals(system)).map(s -> s.key).findAny();
            if (systemKey.isEmpty()) {
                throw new WebApplicationException("Cannot find specified system: " + system, Status.EXPECTATION_FAILED);
            }
        }

        // create the instance
        var result = createInstanceFromTemplateRequest(mp.getNamedMasterOrSelf(hive, server), systemKey.orElse(null),
                responseFile, productDto.key, groupToNode, null, responseFile.fixedVariables, purpose);

        // sync in case of success
        if (result.status != InstanceTemplateReferenceStatus.ERROR) {
            rc.initResource(new ManagedServersResourceImpl()).synchronize(group, server);
        }

        return result;
    }

    InstanceTemplateReferenceResultDto createInstanceFromTemplateRequest(RemoteService remote, Manifest.Key systemKey,
            InstanceTemplateReferenceDescriptor inst, Manifest.Key productKey, Map<String, String> groupToNode,
            TemplateVariableResolver parentTvr, List<TemplateVariableFixedValueOverride> overrides, InstancePurpose purpose) {
        ProductManifest pmf = ProductManifest.of(hive, productKey);

        // check minimum minion Version
        String minMinionVersionString = pmf.getProductDescriptor().minMinionVersion;
        if (StringUtil.isNotBlank(minMinionVersionString)) {
            Version minMinionVersion;
            try {
                minMinionVersion = VersionHelper.parse(minMinionVersionString);
            } catch (RuntimeException e) {
                return new InstanceTemplateReferenceResultDto(inst.name, InstanceTemplateReferenceStatus.ERROR,
                        "Failed to parse minimum BDeploy version '" + minMinionVersionString + "' of product " + productKey);
            }
            if (ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, context).getVersion().version
                    .compareTo(minMinionVersion) < 0) {
                return new InstanceTemplateReferenceResultDto(inst.name, InstanceTemplateReferenceStatus.ERROR,
                        "Creation of instance aborted because minion does not meet the minimum BDeploy version of "
                                + minMinionVersionString);
            }
        }

        Optional<FlattenedInstanceTemplateConfiguration> instTemplate = pmf.getInstanceTemplates().stream()
                .filter(t -> t.name.equals(inst.templateName)).findAny();

        if (instTemplate.isEmpty()) {
            return new InstanceTemplateReferenceResultDto(inst.name, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot find instance template " + inst.templateName);
        }

        TemplateVariableResolver tvr = new TemplateVariableResolver(instTemplate.get().directlyUsedTemplateVars, overrides,
                parentTvr);

        String expandedName = TemplateHelper.process(inst.name, tvr, Variables.TEMPLATE.shouldResolve());
        var nodeStates = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNodes();

        InstanceConfiguration cfg = new InstanceConfiguration();
        cfg.id = UuidHelper.randomId();
        cfg.name = expandedName;
        cfg.description = TemplateHelper.process(inst.description, tvr, Variables.TEMPLATE.shouldResolve());
        cfg.product = productKey;
        cfg.productFilterRegex = inst.productVersionRegex;
        cfg.autoStart = getFirstNonNull(cfg.autoStart, inst.autoStart, instTemplate.map(tpl -> tpl.autoStart).orElse(null));
        cfg.autoUninstall = getFirstNonNull(cfg.autoUninstall, inst.autoUninstall,
                instTemplate.map(tpl -> tpl.autoUninstall).orElse(null));

        cfg.system = systemKey;
        // cfg.configTree = pmf.getConfigTemplateTreeId(); // not allowed.
        cfg.purpose = purpose;
        cfg.instanceVariables = createInstanceVariablesFromTemplate(instTemplate.get(), tvr, pmf);

        SystemConfiguration system = systemKey != null ? SystemManifest.of(hive, systemKey).getConfiguration() : null;
        List<ApplicationManifest> apps = getApps(pmf);
        List<InstanceNodeConfigurationDto> nodes;
        try {
            nodes = createInstanceNodesFromTemplate(cfg, instTemplate.get(), groupToNode, apps, tvr, nodeStates, system,
                    pmf.getProduct());
        } catch (Exception e) {
            log.warn("Exception while creating instance {} through instance template {} from system template", cfg.name,
                    inst.templateName, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Failed to apply template: " + e.toString());
        }

        // Create update from the current product's config template tree, this is the starting point.
        // We cannot simply set the tree ID, since the product on the target may not be available (yet).
        List<FileStatusDto> cfgFiles = InstanceResourceImpl.getUpdatesFromTree(hive, "", new ArrayList<>(),
                pmf.getConfigTemplateTreeId());
        InstanceConfigurationDto configDto = new InstanceConfigurationDto(cfg, nodes);

        try {
            // since the config files are grabbed from the product tree, we validate them as "existing" files.
            // this way the behavior is consistent, since the user did not "change" any file. further down we
            // then need to pass those files as per usual (update), as the target server may not have the product
            // and thus cannot load those "existing" files.
            List<ApplicationValidationDto> validation = pus.validate(new InstanceUpdateDto(configDto, null), apps, system,
                    cfgFiles);
            if (!validation.isEmpty()) {
                validation.forEach(v -> log.warn("Validation problem in instance: {}, app: {}, param: {}: {}", cfg.name, v.appId,
                        v.paramId, v.message));
                ApplicationValidationDto firstMessage = validation.getFirst();
                return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                        "Failed to validate instance, first message: " + firstMessage.message + " (" + firstMessage.appId + ", "
                                + firstMessage.paramId + ")");
            }
        } catch (Exception e) {
            log.warn("Exception validating instance {} created from system template", cfg.name, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot validate instance: " + e.toString());
        }

        try {
            ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNamedMaster(group)
                    .update(new InstanceUpdateDto(configDto, cfgFiles), null);
        } catch (Exception e) {
            log.warn("Cannot create instance {} for system template.", cfg.name, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot create instance: " + e.toString());
        }

        return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.OK,
                "Successfully created instance with ID " + cfg.id);
    }

    private static void sanitizeInstanceTemplateReferenceDescriptor(InstanceTemplateReferenceDescriptor responseFile) {
        if (responseFile.defaultMappings == null) {
            responseFile.defaultMappings = Collections.emptyList();
        }
        if (responseFile.fixedVariables == null) {
            responseFile.fixedVariables = Collections.emptyList();
        }
    }

    private static FlattenedInstanceTemplateConfiguration getInstanceTemplateConfig(
            Collection<FlattenedInstanceTemplateConfiguration> instanceTemplates, String templateName,
            String exceptionMessageDetail) {
        return instanceTemplates.stream().filter(t -> t.name.equals(templateName)).findFirst()
                .orElseThrow(() -> new WebApplicationException(
                        "Cannot find specified instance template: " + templateName + " in " + exceptionMessageDetail,
                        Status.EXPECTATION_FAILED));
    }

    private static Map<String, String> checkVariablesAndCreateGroupToNodeMapping(InstanceTemplateReferenceDescriptor responseFile,
            Collection<TemplateVariable> directlyUsedTemplateVars,
            Collection<FlattenedInstanceTemplateGroupConfiguration> groups) {
        // verify that all mandatory template variables are set
        validateVariablesOrThrow(directlyUsedTemplateVars, responseFile.fixedVariables, "directly in the instance template");

        // create group to node mapping
        Map<String, String> groupToNode = new TreeMap<>();
        for (FlattenedInstanceTemplateGroupConfiguration grp : groups) {
            var mapping = responseFile.defaultMappings.stream().filter(g -> g.group.equals(grp.name)).findFirst().orElse(null);
            if (mapping == null || mapping.node == null) {
                continue; // ignored
            }

            // verify that all mandatory group variables are set
            validateVariablesOrThrow(grp.groupVariables, responseFile.fixedVariables, "in group " + grp.name);

            groupToNode.put(grp.name, mapping.node);
        }
        return groupToNode;
    }

    private static void validateVariablesOrThrow(Collection<TemplateVariable> tvars,
            Collection<TemplateVariableFixedValueOverride> tvarOverrides, String msg) {
        for (TemplateVariable tvar : tvars) {
            if (StringHelper.isNullOrBlank(tvar.defaultValue) && tvarOverrides.stream().noneMatch(v -> v.id.equals(tvar.id))) {
                throw new WebApplicationException("Template Variable " + tvar.id + " not provided, required " + msg,
                        Status.EXPECTATION_FAILED);
            }
        }
    }

    private List<ApplicationManifest> getApps(ProductManifest pmf) {
        return pmf.getApplications().stream().map(k -> ApplicationManifest.of(hive, k, pmf)).toList();
    }

    private static List<InstanceNodeConfigurationDto> createInstanceNodesFromTemplate(InstanceConfiguration config,
            FlattenedInstanceTemplateConfiguration tpl, Map<String, String> groupToNode, List<ApplicationManifest> apps,
            TemplateVariableResolver tvr, Map<String, MinionStatusDto> nodeStates, SystemConfiguration system,
            String productName) {
        Function<String, LinkedValueConfiguration> globalLookup = id -> {
            LinkedValueConfiguration lv = null;
            for (var g : tpl.groups) {
                for (var a : g.applications) {
                    var atvr = new TemplateVariableResolver(a.templateVariables, Collections.emptyList(), tvr);
                    for (var p : a.startParameters) {
                        if (p.id.equals(id) && p.value != null && !p.value.isBlank()) {
                            String value = TemplateHelper.process(p.value, atvr, Variables.TEMPLATE.shouldResolve());
                            lv = new LinkedValueConfiguration(value);
                        }
                    }
                }
            }
            return lv;
        };

        String productNameWithDash = productName + '/';
        BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter = (n, o) -> a -> {
            String fullProductName = productNameWithDash + n;
            Key applicationManifestKey = a.getKey();
            if (o != null) {
                // need to check OS as well.
                var smk = ScopedManifestKey.parse(applicationManifestKey);
                return smk != null && o == smk.getOperatingSystem() && fullProductName.equals(smk.getName());
            }
            return applicationManifestKey.getName().startsWith(fullProductName); // may or may not have *any* OS in the key.
        };

        List<InstanceNodeConfigurationDto> result = new ArrayList<>();
        Set<String> nodes = nodeStates.keySet();
        for (var tgroup : tpl.groups) {
            String targetNode = groupToNode.get(tgroup.name);
            if (targetNode == null || targetNode.isBlank()) {
                continue; // nope
            }
            targetNode = TemplateHelper.process(targetNode, tvr, Variables.TEMPLATE.shouldResolve());
            if (InstanceManifest.CLIENT_NODE_LABEL.equals(targetNode)) {
                targetNode = InstanceManifest.CLIENT_NODE_NAME;
            }
            if (!nodes.contains(targetNode) && !InstanceManifest.CLIENT_NODE_NAME.equals(targetNode)
                    && !InstanceManifest.CLIENT_NODE_LABEL.equals(targetNode)) {
                throw new WebApplicationException(
                        "Group " + tgroup.name + " is mapped to node " + targetNode + " but that node cannot be found",
                        Status.EXPECTATION_FAILED);
            }

            String mappedToNode = targetNode;

            InstanceNodeConfigurationDto node = result.stream().filter(n -> n.nodeName.equals(mappedToNode)).findFirst()
                    .orElseGet(() -> {
                        var r = new InstanceNodeConfigurationDto(mappedToNode, new InstanceNodeConfiguration());
                        r.nodeConfiguration.copyRedundantFields(config);
                        r.nodeConfiguration.mergeVariables(config, system, null);
                        r.nodeConfiguration.controlGroups.addAll(createControlGroupsFromTemplate(tpl, tvr));
                        r.nodeConfiguration.nodeType = mappedToNode.equals(InstanceManifest.CLIENT_NODE_NAME) ? NodeType.CLIENT
                                : (nodeStates.get(mappedToNode).config.minionNodeType == MinionDto.MinionNodeType.MULTI ? NodeType.MULTI : NodeType.SERVER);
                        result.add(r);
                        return r;
                    });

            if (tgroup.type == ApplicationType.CLIENT) {
                createApplicationsForClientGroup(node, tgroup, apps, tvr, appFilter, globalLookup);
            } else {
                createApplicationsForServerGroup(node, tgroup, apps, tvr, nodeStates.get(node.nodeName), appFilter, globalLookup);
            }
        }

        return result;
    }

    private static void createApplicationsForClientGroup(InstanceNodeConfigurationDto node,
            FlattenedInstanceTemplateGroupConfiguration group, List<ApplicationManifest> apps, TemplateVariableResolver tvr,
            BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter,
            Function<String, LinkedValueConfiguration> globalLookup) {
        // no process control groups on clients, but platform for application is not required to match the server.
        ApplicationType groupType = group.type == null ? ApplicationType.SERVER : group.type;
        for (var reqApp : group.applications) {
            List<ApplicationManifest> matchingClients = apps.stream().filter(appFilter.apply(reqApp.application, null)).toList();
            for (var clientApp : matchingClients) {
                ApplicationType appType = clientApp.getDescriptor().type;
                if (groupType != appType) {
                    throw new WebApplicationException(
                            "Incompatible application type. Node has type " + group.type + " but application has type " + appType,
                            Status.EXPECTATION_FAILED);
                }

                node.nodeConfiguration.applications
                        .add(createApplicationFromTemplate(reqApp, clientApp, node, tvr, globalLookup));
            }
        }

    }

    private static void createApplicationsForServerGroup(InstanceNodeConfigurationDto node,
            FlattenedInstanceTemplateGroupConfiguration group, List<ApplicationManifest> apps, TemplateVariableResolver tvr,
            MinionStatusDto status, BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter,
            Function<String, LinkedValueConfiguration> globalLookup) {
        OperatingSystem targetOs = status.config.os;
        ApplicationType groupType = group.type == null ? ApplicationType.SERVER : group.type;

        for (var reqApp : group.applications) {
            List<OperatingSystem> applyOn = reqApp.applyOn;
            if (applyOn != null && !applyOn.isEmpty() && !applyOn.contains(targetOs)) {
                log.debug("Skipping application {}, not applicable to {}", reqApp.name, targetOs);
                continue;
            }

            ApplicationManifest appManifest = apps.stream().filter(appFilter.apply(reqApp.application, targetOs)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cannot find application with ID " + reqApp.application
                            + " while creating application from template: " + reqApp.name));

            ApplicationType appType = appManifest.getDescriptor().type;
            if (groupType != appType) {
                throw new WebApplicationException(
                        "Incompatible application type. Node has type " + group.type + " but application has type " + appType,
                        Status.EXPECTATION_FAILED);
            }

            ApplicationConfiguration cfg = createApplicationFromTemplate(reqApp, appManifest, node, tvr, globalLookup);

            Optional<ProcessControlGroupConfiguration> targetCg = node.nodeConfiguration.controlGroups.stream()
                    .filter(g -> g.name.equals(reqApp.preferredProcessControlGroup)).findAny();

            // if preferred group is not found, the application will drop in the default group later during saving.
            targetCg.ifPresent(pcg -> pcg.processOrder.add(cfg.id));

            node.nodeConfiguration.applications.add(cfg);
        }
    }

    private static ApplicationConfiguration createApplicationFromTemplate(FlattenedApplicationTemplateConfiguration reqApp,
            ApplicationManifest am, InstanceNodeConfigurationDto node, TemplateVariableResolver parentTvr,
            Function<String, LinkedValueConfiguration> globalLookup) {
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        ApplicationDescriptor appDesc = am.getDescriptor();
        TemplateVariableResolver tvr = new TemplateVariableResolver(reqApp.templateVariables, Collections.emptyList(), parentTvr);

        cfg.id = UuidHelper.randomId();
        cfg.name = reqApp.name == null ? am.getDescriptor().name
                : TemplateHelper.process(reqApp.name, tvr, Variables.TEMPLATE.shouldResolve());
        cfg.application = am.getKey();
        cfg.pooling = appDesc.pooling;
        cfg.processControl = new ProcessControlConfiguration();

        applyProcessControl(cfg, appDesc, reqApp);

        // each application's endpoints start out as copy of the default. no need to copy as the template
        // was sent through REST already.
        cfg.endpoints.http = appDesc.endpoints.http;

        var resolver = ProductUpdateService.createResolver(node, cfg);

        if (appDesc.startCommand != null) {
            cfg.start = createCommand(appDesc.startCommand, reqApp.startParameters, appDesc, tvr, resolver, globalLookup);
        }
        if (appDesc.stopCommand != null) {
            cfg.stop = createCommand(appDesc.stopCommand, Collections.emptyList(), appDesc, tvr, resolver, globalLookup);
        }

        return cfg;
    }

    private static void applyProcessControl(ApplicationConfiguration cfg, ApplicationDescriptor appDesc,
            FlattenedApplicationTemplateConfiguration reqApp) {

        // defaults.
        cfg.processControl.attachStdin = appDesc.processControl.attachStdin;
        if (appDesc.processControl.supportedStartTypes != null && !appDesc.processControl.supportedStartTypes.isEmpty()) {
            cfg.processControl.startType = appDesc.processControl.supportedStartTypes.get(0);
        } else {
            cfg.processControl.startType = ApplicationStartType.MANUAL;
        }
        cfg.processControl.keepAlive = appDesc.processControl.supportsKeepAlive;
        cfg.processControl.noOfRetries = (int) appDesc.processControl.noOfRetries;
        cfg.processControl.gracePeriod = appDesc.processControl.gracePeriod;
        cfg.processControl.attachStdin = appDesc.processControl.attachStdin;
        cfg.processControl.startupProbe = appDesc.processControl.startupProbe;
        cfg.processControl.livenessProbe = appDesc.processControl.livenessProbe;
        cfg.processControl.configDirs = appDesc.processControl.configDirs;
        cfg.processControl.autostart = appDesc.processControl.supportsAutostart;

        // now apply from template in case things are set.
        applyFromPCTplMap(cfg.processControl, reqApp.processControl);

        // verify process control validity
        var processControlDescriptor = appDesc.processControl;
        var processControlConfiguration = cfg.processControl;
        if (!processControlDescriptor.supportsKeepAlive && processControlConfiguration.keepAlive) {
            throw new WebApplicationException("Invalid application template '" + reqApp.name
                    + "'. Template has 'keepAlive' enabled, but its descriptor forbids it.", Status.EXPECTATION_FAILED);
        }
        if (!processControlDescriptor.supportsAutostart && processControlConfiguration.autostart) {
            throw new WebApplicationException("Invalid application template '" + reqApp.name
                    + "'. Template has 'autostart' enabled, but its descriptor forbids it.", Status.EXPECTATION_FAILED);
        }
    }

    private static void applyFromPCTplMap(ProcessControlConfiguration target, Map<String, Object> tpl) {
        ObjectMapper om = JacksonHelper.getDefaultJsonObjectMapper();

        try {
            // easiest way to convert the generic template map to something usable and typed.
            String json = om.writeValueAsString(tpl);
            ProcessControlConfiguration pcc = om.readValue(json, ProcessControlConfiguration.class);

            for (String k : tpl.keySet()) {
                applySingleFromPCTplMap(target, pcc, k);
            }
        } catch (Exception e) {
            log.warn("Cannot process process control information", e);
        }
    }

    private static void applySingleFromPCTplMap(ProcessControlConfiguration target, ProcessControlConfiguration source,
            String key) {
        try {
            Field field = ProcessControlConfiguration.class.getField(key);
            field.set(target, field.get(source));
        } catch (Exception e) {
            log.warn("Cannot apply process control field value", e);
        }
    }

    private static CommandConfiguration createCommand(ExecutableDescriptor command, List<TemplateParameter> tplParameters,
            ApplicationDescriptor ad, TemplateVariableResolver tvr, VariableResolver resolver,
            Function<String, LinkedValueConfiguration> globalLookup) {
        CommandConfiguration result = new CommandConfiguration();
        List<ParameterConfiguration> allParams = new ArrayList<>();

        result.executable = TemplateHelper.process(command.launcherPath, tvr, Variables.TEMPLATE.shouldResolve());

        for (var pd : command.parameters) {
            Optional<TemplateParameter> tp = tplParameters.stream().filter(t -> t.id.equals(pd.id)).findAny();

            if (tp.isEmpty() && !pd.mandatory) {
                continue;
            }

            var val = pd.defaultValue;
            var globalLv = pd.global ? globalLookup.apply(pd.id) : null;
            if (globalLv != null) {
                val = globalLv;
            } else if (tp.isPresent() && tp.get().value != null && !tp.get().value.isBlank()) {
                var exp = TemplateHelper.process(tp.get().value, tvr, Variables.TEMPLATE.shouldResolve());
                val = new LinkedValueConfiguration(exp);
            }

            var pc = new ParameterConfiguration();

            pc.id = pd.id;
            pc.value = val;
            pc.preRender(pd);

            allParams.add(pc);
        }

        // a resolver which can temporarily resolve from our own list or parameters.
        CompositeResolver r = new CompositeResolver();
        r.add(new FixedParameterListValueResolver(allParams)); // temporary allow all our own parameters for resolution.
        r.add(resolver);

        for (var p : allParams) {
            var pd = command.parameters.stream().filter(x -> x.id.equals(p.id)).findAny().orElseThrow(); // MUST exist

            if (ProductUpdateService.meetsCondition(ad, pd, r)) {
                result.parameters.add(p);
            }
        }

        return result;
    }

    private static List<ProcessControlGroupConfiguration> createControlGroupsFromTemplate(
            FlattenedInstanceTemplateConfiguration tpl, TemplateVariableResolver tvr) {
        List<ProcessControlGroupConfiguration> pcgcs = new ArrayList<>();

        for (var g : tpl.processControlGroups) {
            var pcg = new ProcessControlGroupConfiguration();
            pcg.name = TemplateHelper.process(g.name, tvr, Variables.TEMPLATE.shouldResolve());
            pcg.startType = g.startType;
            pcg.startWait = g.startWait;
            pcg.stopType = g.stopType;

            pcgcs.add(pcg);
        }

        return pcgcs;
    }

    private static List<VariableConfiguration> createInstanceVariablesFromTemplate(FlattenedInstanceTemplateConfiguration tpl,
            TemplateVariableResolver tvr, ProductManifest pmf) {

        List<VariableConfiguration> result = new ArrayList<>();

        for (var v : tpl.instanceVariables) {
            v.value = new LinkedValueConfiguration(v.value == null ? ""
                    : TemplateHelper.process(v.value.getPreRenderable(), tvr, Variables.TEMPLATE.shouldResolve()));
            result.add(v);
        }

        var instanceVariableValues = Optional.ofNullable(tpl.instanceVariableValues).orElseGet(Collections::emptyList);
        for (var pv : pmf.getInstanceVariables()) {
            if (result.stream().anyMatch(v -> v.id.equals(pv.id))) {
                continue;
            }
            var instanceVariable = new VariableConfiguration(pv);
            var ivv = instanceVariableValues.stream().filter(i -> i.id.equals(instanceVariable.id)).findFirst().orElse(null);
            if (ivv != null) {
                instanceVariable.value = new LinkedValueConfiguration(
                        TemplateHelper.process(ivv.value.getPreRenderable(), tvr, Variables.TEMPLATE.shouldResolve()));
            }
            result.add(instanceVariable);
        }

        return result;
    }

    private static boolean getFirstNonNull(boolean defaultValue, Boolean... valueInOrderOfPriority) {
        for (Boolean value : valueInOrderOfPriority) {
            if (null != value) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * find, import and verify product
     *
     * @param group the identifier of the instance group
     * @param responseFile the descriptor based on which we are looking up the product
     * @param productName optional filter, if not <code>null</code> will look up products with that name
     * @return a productDto matching the responseFile
     */
    private ProductDto findOrImportProduct(String group, InstanceTemplateReferenceDescriptor responseFile, String productName) {
        ProductResource pr = rc.initResource(new ProductResourceImpl(hive, group));
        // searching in the existing product list (pr.list)
        return InstanceTemplateHelper.findMatchingProduct(responseFile, pr.list(productName)).orElseGet(() -> {
            // create a product request
            LatestProductVersionRequestDto req = new LatestProductVersionRequestDto();
            req.productId = responseFile.productId;
            req.version = InstanceTemplateHelper.getInitialProductVersionRegex(responseFile);
            req.regex = true;
            req.instanceTemplate = responseFile.templateName;
            SoftwareRepositoryResourceImpl srr = rc.initResource(new SoftwareRepositoryResourceImpl());
            ProductKeyWithSourceDto toImport = srr.getLatestProductVersion(req);
            // import it
            pr.copyProduct(toImport.groupOrRepo, toImport.key.getName(), List.of(toImport.key.getTag()));
            // look it up again
            return InstanceTemplateHelper.findMatchingProduct(responseFile, pr.list(productName)).get();
        });
    }
}
