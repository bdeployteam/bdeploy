package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.VariableResolver;
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
import io.bdeploy.interfaces.configuration.template.TrackingTemplateOverrideResolver;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateInstanceTemplateGroupMapping;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.FixedParameterListValueResolver;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.api.InstanceTemplateResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.ProductDto;
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
    public InstanceTemplateReferenceResultDto createFromTemplate(InstanceTemplateReferenceDescriptor instance,
            InstancePurpose purpose, String server) {
        RemoteService remote = mp.getNamedMasterOrSelf(hive, server);

        // 0. normalize input
        if (instance.fixedVariables == null) {
            instance.fixedVariables = Collections.emptyList();
        }
        if (instance.defaultMappings == null) {
            instance.defaultMappings = Collections.emptyList();
        }

        // 1. find and verify product.
        ProductResource pr = rc.initResource(new ProductResourceImpl(hive, group));
        List<ProductDto> products = pr.list(null);
        Optional<ProductDto> product = findMatchingProductOrFail(instance, products);

        // 2. find and verify all group mappings and whether all variables are set for each required group.
        Map<String, MinionStatusDto> nodes = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context)
                .getNodes();

        FlattenedInstanceTemplateConfiguration tpl = product.get().instanceTemplates.stream()
                .filter(t -> t.name.equals(instance.templateName)).findFirst()
                .orElseThrow(() -> new WebApplicationException("Cannot find specified instance template: " + instance.templateName
                        + " in best matching product version " + product.get().key, Status.EXPECTATION_FAILED));

        Map<String, String> groupToNode = new TreeMap<>();
        for (FlattenedInstanceTemplateGroupConfiguration group : tpl.groups) {
            SystemTemplateInstanceTemplateGroupMapping mapping = instance.defaultMappings.stream()
                    .filter(g -> g.group.equals(group.name)).findFirst().orElseThrow(() -> new WebApplicationException(
                            "The template does not specify a mapping for group " + group.name, Status.EXPECTATION_FAILED));

            if (mapping == null) {
                continue; // ignored
            }

            if (nodes.get(mapping.node) == null) {
                throw new WebApplicationException(
                        "Group " + group.name + " is mapped to node " + mapping.node + " but that node cannot be found",
                        Status.EXPECTATION_FAILED);
            }

            groupToNode.put(group.name, mapping.node);

            // otherwise check variables.
            for (TemplateVariable tvar : group.groupVariables) {
                instance.fixedVariables.stream().filter(v -> v.id.equals(tvar.id)).findFirst()
                        .orElseThrow(() -> new WebApplicationException(
                                "Template Variable " + tvar.id + " not provided, required in group " + group.name,
                                Status.EXPECTATION_FAILED));
            }
        }

        // 3. setup tracking for variables, and verify all variables are set.
        for (TemplateVariable tvar : tpl.directlyUsedTemplateVars) {
            instance.fixedVariables.stream().filter(v -> v.id.equals(tvar.id)).findFirst()
                    .orElseThrow(() -> new WebApplicationException(
                            "Template Variable " + tvar.id + " not provided, required directly in the instance template",
                            Status.EXPECTATION_FAILED));
        }

        TrackingTemplateOverrideResolver ttor = new TrackingTemplateOverrideResolver(instance.fixedVariables);

        // 4. finally create the instance on the target.
        var result = createInstanceFromTemplateRequest(remote, null, instance, product.get().key, groupToNode, ttor, purpose);

        if (result.status != InstanceTemplateReferenceStatus.ERROR) {
            // sync in case of central and success... :)
            ManagedServersResource msr = rc.initResource(new ManagedServersResourceImpl());
            msr.synchronize(group, server);
        }

        return result;
    }

    Optional<ProductDto> findMatchingProductOrFail(InstanceTemplateReferenceDescriptor instance, List<ProductDto> products) {
        boolean hasRegex = !(instance.productVersionRegex == null || instance.productVersionRegex.isBlank()
                || instance.productVersionRegex.equals(".*"));

        // the list is ordered - the first matching product is also the best matching version of that product.
        Optional<ProductDto> product = products.stream().filter(p -> {
            if (!p.product.equals(instance.productId)) {
                return false;
            }

            // check whether the version pattern is fulfilled
            if (hasRegex && !Pattern.matches(instance.productVersionRegex, p.key.getTag())) {
                return false;
            }

            // check whether requested template is in this version, otherwise reject.
            return p.instanceTemplates.stream().anyMatch(t -> t.name.equals(instance.templateName));
        }).findFirst();

        if (product.isEmpty()) {
            throw new WebApplicationException(
                    "Cannot find matching product with ID '" + instance.productId
                            + (hasRegex ? ("' (with version matching: " + instance.productVersionRegex + ")") : "'")
                            + " or matching version does not have instance template named '" + instance.templateName + "'",
                    Status.NOT_ACCEPTABLE);
        }

        return product;
    }

    InstanceTemplateReferenceResultDto createInstanceFromTemplateRequest(RemoteService remote, Manifest.Key systemKey,
            InstanceTemplateReferenceDescriptor inst, Manifest.Key productKey, Map<String, String> groupToNode,
            TrackingTemplateOverrideResolver ttor, InstancePurpose purpose) {

        String expandedName = TemplateHelper.process(inst.name, ttor, ttor::canResolve);
        ProductManifest pmf = ProductManifest.of(hive, productKey);

        Optional<FlattenedInstanceTemplateConfiguration> instTemplate = pmf.getInstanceTemplates().stream()
                .filter(t -> t.name.equals(inst.templateName)).findAny();

        if (instTemplate.isEmpty()) {
            return new InstanceTemplateReferenceResultDto(expandedName, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot find instance template " + inst.templateName);
        }

        var nodeStates = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNodes();

        // create update from the current product's config template tree, this is the starting point.
        // we cannot simply set the tree ID, since the product on the target may not be available (yet).
        List<FileStatusDto> cfgFiles = InstanceResourceImpl.getUpdatesFromTree(hive, "", new ArrayList<>(),
                pmf.getConfigTemplateTreeId());

        InstanceConfiguration cfg = new InstanceConfiguration();
        cfg.id = UuidHelper.randomId();
        cfg.name = expandedName;
        cfg.description = TemplateHelper.process(inst.description, ttor, ttor::canResolve);
        cfg.product = productKey;
        cfg.productFilterRegex = inst.productVersionRegex;
        cfg.system = systemKey;
        // cfg.configTree = pmf.getConfigTemplateTreeId(); // not allowed.
        cfg.purpose = purpose;

        List<ApplicationManifest> apps = pmf.getApplications().stream().map(k -> ApplicationManifest.of(hive, k, pmf)).toList();

        cfg.instanceVariables = createInstanceVariablesFromTemplate(instTemplate.get(), ttor);

        List<InstanceNodeConfigurationDto> nodes;
        try {
            nodes = createInstanceNodesFromTemplate(cfg, instTemplate.get(), groupToNode, apps, ttor, nodeStates, (n, o) -> a -> {
                if (o != null) {
                    // need to check OS as well.
                    var smk = ScopedManifestKey.parse(a.getKey());
                    return o.equals(smk.getOperatingSystem()) && smk.getName().equals(pmf.getProduct() + "/" + n);
                } else {
                    return a.getKey().getName().startsWith(pmf.getProduct() + "/" + n); // may or may not have *any* OS in the key.
                }
            });
        } catch (Exception e) {
            log.warn("Exception while creating instance {} through instance template {} from system template", cfg.name,
                    inst.templateName, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Failed to apply template: " + e.toString());
        }

        InstanceUpdateDto iud = new InstanceUpdateDto(new InstanceConfigurationDto(cfg, nodes), cfgFiles);

        try {
            SystemConfiguration sc = systemKey != null ? SystemManifest.of(hive, systemKey).getConfiguration() : null;
            List<ApplicationValidationDto> validation = pus.validate(iud, apps, sc);
            if (!validation.isEmpty()) {
                validation.forEach(v -> log.warn("Validation problem in instance: {}, app: {}, param: {}: {}", cfg.name, v.appId,
                        v.paramId, v.message));
                return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                        "Failed to validate instance, first message: " + validation.get(0).message);
            }
        } catch (Exception e) {
            log.warn("Exception validating instance {} created from system template", cfg.name, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot validate instance: " + e.toString());
        }

        try {
            ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNamedMaster(group).update(iud,
                    null);
        } catch (Exception e) {
            log.warn("Cannot create instance {} for system template.", cfg.name, e);
            return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.ERROR,
                    "Cannot create instance: " + e.toString());
        }

        return new InstanceTemplateReferenceResultDto(cfg.name, InstanceTemplateReferenceStatus.OK,
                "Successfully created instance with ID " + cfg.id);
    }

    private static List<InstanceNodeConfigurationDto> createInstanceNodesFromTemplate(InstanceConfiguration config,
            FlattenedInstanceTemplateConfiguration tpl, Map<String, String> groupToNode, List<ApplicationManifest> apps,
            TrackingTemplateOverrideResolver ttor, Map<String, MinionStatusDto> nodeStates,
            BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter) {
        List<InstanceNodeConfigurationDto> result = new ArrayList<>();

        Function<String, LinkedValueConfiguration> globalLookup = id -> {
            for (var n : result) {
                for (var a : n.nodeConfiguration.applications) {
                    for (var p : a.start.parameters) {
                        if (p.id.equals(id)) {
                            return p.value;
                        }
                    }
                }
            }
            return null;
        };

        for (var tgroup : tpl.groups) {
            String mappedToNode = groupToNode.get(tgroup.name);
            if (mappedToNode == null || mappedToNode.isBlank()) {
                continue; // nope
            }

            InstanceNodeConfigurationDto node = result.stream().filter(n -> n.nodeName.equals(mappedToNode)).findFirst()
                    .orElseGet(() -> {
                        var r = new InstanceNodeConfigurationDto(mappedToNode);

                        r.nodeConfiguration = new InstanceNodeConfiguration();
                        r.nodeConfiguration.copyRedundantFields(config);

                        // NO need to care about (redundant!) instance variables, etc. This is done when saving the instance.

                        r.nodeConfiguration.controlGroups.addAll(createControlGroupsFromTemplate(tpl, ttor));

                        result.add(r);
                        return r;
                    });

            if (tgroup.type == ApplicationType.CLIENT) {
                createApplicationsForClientGroup(node, tgroup, apps, ttor, appFilter, globalLookup);
            } else {
                createApplicationsForServerGroup(node, tgroup, apps, ttor, nodeStates.get(node.nodeName), appFilter,
                        globalLookup);
            }
        }

        return result;
    }

    private static void createApplicationsForClientGroup(InstanceNodeConfigurationDto node,
            FlattenedInstanceTemplateGroupConfiguration group, List<ApplicationManifest> apps,
            TrackingTemplateOverrideResolver ttor, BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter,
            Function<String, LinkedValueConfiguration> globalLookup) {
        // no process control groups on clients, but platform for application is not required to match the server.
        for (var reqApp : group.applications) {
            List<ApplicationManifest> matchingClients = apps.stream().filter(appFilter.apply(reqApp.application, null)).toList();
            for (var clientApp : matchingClients) {
                node.nodeConfiguration.applications
                        .add(createApplicationFromTemplate(reqApp, clientApp, node, ttor, globalLookup));
            }
        }

    }

    private static void createApplicationsForServerGroup(InstanceNodeConfigurationDto node,
            FlattenedInstanceTemplateGroupConfiguration group, List<ApplicationManifest> apps,
            TrackingTemplateOverrideResolver ttor, MinionStatusDto status,
            BiFunction<String, OperatingSystem, Predicate<ApplicationManifest>> appFilter,
            Function<String, LinkedValueConfiguration> globalLookup) {
        OperatingSystem targetOs = status.config.os;

        for (var reqApp : group.applications) {
            ApplicationConfiguration cfg = createApplicationFromTemplate(reqApp,
                    apps.stream().filter(appFilter.apply(reqApp.application, targetOs)).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Cannot find application with ID " + reqApp.application
                                    + " while creating application from template: " + reqApp.name)),
                    node, ttor, globalLookup);

            Optional<ProcessControlGroupConfiguration> targetCg = node.nodeConfiguration.controlGroups.stream()
                    .filter(g -> g.name.equals(reqApp.preferredProcessControlGroup)).findAny();
            // if preferred group is not found, the application will drop in the default group later during saving.
            if (targetCg.isPresent()) {
                targetCg.get().processOrder.add(cfg.id);
            }

            node.nodeConfiguration.applications.add(cfg);
        }
    }

    private static ApplicationConfiguration createApplicationFromTemplate(FlattenedApplicationTemplateConfiguration reqApp,
            ApplicationManifest am, InstanceNodeConfigurationDto node, TrackingTemplateOverrideResolver ttor,
            Function<String, LinkedValueConfiguration> globalLookup) {
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        ApplicationDescriptor appDesc = am.getDescriptor();

        cfg.id = UuidHelper.randomId();
        cfg.name = TemplateHelper.process(reqApp.name, ttor, ttor::canResolve);
        cfg.application = am.getKey();
        cfg.pooling = appDesc.pooling;
        cfg.processControl = new ProcessControlConfiguration();

        applyProcessControl(cfg, appDesc);

        // each application's endpoints start out as copy of the default. no need to copy as the template
        // was sent through REST already.
        cfg.endpoints.http = appDesc.endpoints.http;

        var resolver = ProductUpdateService.createResolver(node, cfg);

        if (appDesc.startCommand != null) {
            cfg.start = createCommand(appDesc.startCommand, reqApp.startParameters, appDesc, ttor, resolver, globalLookup);
        }
        if (appDesc.stopCommand != null) {
            cfg.stop = createCommand(appDesc.stopCommand, Collections.emptyList(), appDesc, ttor, resolver, globalLookup);
        }

        return cfg;
    }

    private static void applyProcessControl(ApplicationConfiguration cfg, ApplicationDescriptor appDesc) {
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
        cfg.processControl.configDirs = appDesc.processControl.configDirs;
        cfg.processControl.startupProbe = appDesc.processControl.startupProbe;
        cfg.processControl.lifenessProbe = appDesc.processControl.lifenessProbe;
    }

    private static CommandConfiguration createCommand(ExecutableDescriptor command, List<TemplateParameter> tplParameters,
            ApplicationDescriptor ad, TrackingTemplateOverrideResolver ttor, VariableResolver resolver,
            Function<String, LinkedValueConfiguration> globalLookup) {
        CommandConfiguration result = new CommandConfiguration();
        List<ParameterConfiguration> allParams = new ArrayList<>();

        result.executable = TemplateHelper.process(command.launcherPath, ttor, ttor::canResolve);

        for (var pd : command.parameters) {
            Optional<TemplateParameter> tp = tplParameters.stream().filter(t -> t.id.equals(pd.id)).findAny();

            if (tp.isEmpty() && !pd.mandatory) {
                continue;
            }

            var val = pd.defaultValue;
            if (tp.isPresent()) {
                if (tp.get().value != null && !tp.get().value.isBlank()) {
                    var exp = TemplateHelper.process(tp.get().value, ttor, ttor::canResolve);
                    val = new LinkedValueConfiguration(exp);
                }
            } else if (pd.global) {
                var globalLv = globalLookup.apply(pd.id);
                if (globalLv != null) {
                    val = globalLv;
                }
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
            FlattenedInstanceTemplateConfiguration tpl, TrackingTemplateOverrideResolver ttor) {
        List<ProcessControlGroupConfiguration> pcgcs = new ArrayList<>();

        for (var g : tpl.processControlGroups) {
            var pcg = new ProcessControlGroupConfiguration();
            pcg.name = TemplateHelper.process(g.name, ttor, ttor::canResolve);
            pcg.startType = g.startType;
            pcg.startWait = g.startWait;
            pcg.stopType = g.stopType;

            pcgcs.add(pcg);
        }

        return pcgcs;
    }

    private static List<VariableConfiguration> createInstanceVariablesFromTemplate(FlattenedInstanceTemplateConfiguration tpl,
            TrackingTemplateOverrideResolver ttor) {

        List<VariableConfiguration> result = new ArrayList<>();

        for (var v : tpl.instanceVariables) {
            v.value = new LinkedValueConfiguration(TemplateHelper.process(v.value.getPreRenderable(), ttor, ttor::canResolve));
            result.add(v);
        }

        return result;
    }

}
