package io.bdeploy.ui.api.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
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
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateInstanceReference;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.FixedParameterListValueResolver;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.dto.SystemTemplateDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto.SystemTemplateGroupMapping;
import io.bdeploy.ui.dto.SystemTemplateResultDto;
import io.bdeploy.ui.dto.SystemTemplateResultDto.SystemTemplateInstanceResultDto;
import io.bdeploy.ui.dto.SystemTemplateResultDto.SystemTemplateInstanceStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

public class SystemResourceImpl implements SystemResource {

    private static final Logger log = LoggerFactory.getLogger(SystemResourceImpl.class);

    private final String group;
    private final BHive hive;

    @Inject
    private Minion minion;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private MasterProvider mp;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private ProductUpdateService pus;

    public SystemResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<SystemConfigurationDto> list() {
        List<SystemConfigurationDto> result = new ArrayList<>();
        SortedSet<Key> systems = SystemManifest.scan(hive);
        for (Manifest.Key k : systems) {
            SystemConfigurationDto dto = new SystemConfigurationDto();

            if (minion.getMode() == MinionMode.CENTRAL) {
                dto.minion = new ControllingMaster(hive, k).read().getName();
            }

            dto.key = k;
            dto.config = SystemManifest.of(hive, k).getConfiguration();
            result.add(dto);
        }
        return result;
    }

    @Override
    public Manifest.Key update(SystemConfigurationDto dto) {
        SystemManifest sm = SystemManifest.load(hive, dto.config.id);
        String target = dto.minion;
        RemoteService remote;
        if (sm == null) {
            // new system.
            if (minion.getMode() == MinionMode.CENTRAL) {
                ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
                ManagedMasterDto server = masters.getManagedMaster(target);
                if (server == null) {
                    throw new WebApplicationException("Managed server not found: " + target, Status.NOT_FOUND);
                }

                remote = new RemoteService(UriBuilder.fromUri(server.uri).build(), server.auth);
            } else {
                remote = minion.getSelf();
            }
        } else {
            // existing system.
            remote = mp.getControllingMaster(hive, sm.getKey());
        }

        MasterSystemResource msr = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context)
                .getNamedMaster(group).getSystemResource();

        Manifest.Key key = msr.update(dto.config);

        syncServer(target);

        return key;
    }

    @Override
    public void delete(String systemId) {
        Key key = SystemManifest.load(hive, systemId).getKey();
        RemoteService remote = mp.getControllingMaster(hive, key);
        MasterSystemResource msr = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context)
                .getNamedMaster(group).getSystemResource();

        msr.delete(systemId);
        syncSystem(key);

        if (minion.getMode() != MinionMode.CENTRAL) {
            changes.remove(ObjectChangeType.SYSTEM, key);
        }
    }

    private void syncSystem(Manifest.Key systemKey) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        String master = new ControllingMaster(hive, systemKey).read().getName();
        rs.synchronize(group, master);
    }

    private void syncServer(String name) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        rs.synchronize(group, name);
    }

    @Override
    public SystemTemplateDto loadTemplate(InputStream inputStream, String target) {
        RemoteService remote;
        if (minion.getMode() == MinionMode.CENTRAL) {
            ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
            ManagedMasterDto server = masters.getManagedMaster(target);
            if (server == null) {
                throw new WebApplicationException("Managed server not found: " + target, Status.NOT_FOUND);
            }

            remote = new RemoteService(UriBuilder.fromUri(server.uri).build(), server.auth);
        } else {
            remote = minion.getSelf();
        }

        SystemTemplateDto result = new SystemTemplateDto();

        try (Activity loading = reporter.start("Fetching Nodes...", 4)) {
            // fetch all the node states from the actual remote server which has been seleted.
            result.nodes = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNodes();
            loading.worked(1);
            loading.activity("Loading System Template...");

            // load the actual template from the file
            SystemTemplateDescriptor template = StorageHelper.fromYamlStream(inputStream, SystemTemplateDescriptor.class);

            // verify that instances have been defined in the template.
            if (template.instances == null || template.instances.isEmpty()) {
                throw new WebApplicationException("No instances defined in system template.", Status.NOT_ACCEPTABLE);
            }

            // load all products that are available. this will also give us all templates, so we can assure they are all there.
            loading.worked(1);
            loading.activity("Loading required Products...");
            ProductResource pr = rc.initResource(new ProductResourceImpl(hive, group));
            List<ProductDto> products = pr.list(null);

            // check whether all requested products and the requested template IN the product(s) are present.
            loading.worked(1);
            loading.activity("Validating System Template " + template.name);

            for (SystemTemplateInstanceReference instance : template.instances) {
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
                    throw new WebApplicationException("Cannot find matching product with ID '" + instance.productId
                            + (hasRegex ? ("' (with version matching: " + instance.productVersionRegex + ")") : "'")
                            + " or matching version does not have instance template named '" + instance.templateName + "'",
                            Status.NOT_ACCEPTABLE);
                }

                result.products.add(product.get());
            }

            result.template = template;

            return result;
        }
    }

    @Override
    public SystemTemplateResultDto applyTemplate(SystemTemplateRequestDto request) {
        SystemTemplateResultDto result = new SystemTemplateResultDto();

        List<TemplateVariableFixedValueOverride> overrides = request.templateVariableValues.entrySet().stream()
                .map(e -> new TemplateVariableFixedValueOverride(e.getKey(), e.getValue())).toList();

        TrackingTemplateOverrideResolver ttor = new TrackingTemplateOverrideResolver(overrides);

        // 1. create system & system variables.
        var key = createSystemFromTemplateRequest(request, ttor);

        // 2. create each instance.
        for (var inst : request.template.instances) {
            String instName = TemplateHelper.process(inst.name, ttor, ttor::canResolve);
            var mappings = request.groupMappings.stream().filter(m -> m.instanceName.equals(instName)).findFirst();
            if (mappings.isEmpty()) {
                result.results.add(
                        new SystemTemplateInstanceResultDto(instName, SystemTemplateInstanceStatus.ERROR, "No group mappings."));
                continue;
            }

            if (mappings.get().groupToNode.isEmpty()) {
                result.results.add(
                        new SystemTemplateInstanceResultDto(instName, SystemTemplateInstanceStatus.WARNING, "Instance skipped."));
                continue;
            }

            List<TemplateVariableFixedValueOverride> perInstanceValues = mappings.get().templateVariableValues.entrySet().stream()
                    .map(e -> new TemplateVariableFixedValueOverride(e.getKey(), e.getValue())).toList();

            TrackingTemplateOverrideResolver ittor = new TrackingTemplateOverrideResolver(perInstanceValues, ttor);

            result.results
                    .add(createInstanceFromTemplateRequest(key, inst, mappings.get(), ittor, request.minion, request.purpose));
        }

        // 3. sync after we created everything.
        syncSystem(key);

        return result;
    }

    private SystemTemplateInstanceResultDto createInstanceFromTemplateRequest(Manifest.Key systemKey,
            SystemTemplateInstanceReference inst, SystemTemplateGroupMapping mappings, TrackingTemplateOverrideResolver ttor,
            String minion, InstancePurpose purpose) {

        String expandedName = TemplateHelper.process(inst.name, ttor, ttor::canResolve);
        ProductManifest pmf = ProductManifest.of(hive, mappings.productKey);

        Optional<FlattenedInstanceTemplateConfiguration> instTemplate = pmf.getInstanceTemplates().stream()
                .filter(t -> t.name.equals(inst.templateName)).findAny();

        if (instTemplate.isEmpty()) {
            return new SystemTemplateInstanceResultDto(expandedName, SystemTemplateInstanceStatus.ERROR,
                    "Cannot find instance template " + inst.templateName);
        }

        SystemManifest smf = SystemManifest.of(hive, systemKey);

        // we'll use the same remote as the system, so we can fetch it from there.
        RemoteService remote = mp.getControllingMaster(hive, systemKey);

        var nodeStates = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNodes();

        // create update from the current product's config template tree, this is the starting point.
        // we cannot simply set the tree ID, since the product on the target may not be available (yet).
        List<FileStatusDto> cfgFiles = InstanceResourceImpl.getUpdatesFromTree(hive, "", new ArrayList<>(),
                pmf.getConfigTemplateTreeId());

        InstanceConfiguration cfg = new InstanceConfiguration();
        cfg.id = UuidHelper.randomId();
        cfg.name = expandedName;
        cfg.description = TemplateHelper.process(inst.description, ttor, ttor::canResolve);
        cfg.product = mappings.productKey;
        cfg.productFilterRegex = inst.productVersionRegex;
        cfg.system = systemKey;
        // cfg.configTree = pmf.getConfigTemplateTreeId(); // not allowed.
        cfg.purpose = purpose;

        List<ApplicationManifest> apps = pmf.getApplications().stream().map(k -> ApplicationManifest.of(hive, k, pmf)).toList();

        cfg.instanceVariables = createInstanceVariablesFromTemplate(instTemplate.get(), ttor);

        List<InstanceNodeConfigurationDto> nodes;
        try {
            nodes = createInstanceNodesFromTemplate(cfg, smf, instTemplate.get(), mappings, apps, ttor, nodeStates,
                    (n, o) -> (a) -> {
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
            return new SystemTemplateInstanceResultDto(cfg.name, SystemTemplateInstanceStatus.ERROR,
                    "Failed to apply template: " + e.toString());
        }

        InstanceUpdateDto iud = new InstanceUpdateDto(new InstanceConfigurationDto(cfg, nodes), cfgFiles);

        try {
            List<ApplicationValidationDto> validation = pus.validate(iud, apps, smf.getConfiguration());
            if (!validation.isEmpty()) {
                validation.forEach(v -> log.warn("Validation problem in instance: {}, app: {}, param: {}: {}", cfg.name, v.appId,
                        v.paramId, v.message));
                return new SystemTemplateInstanceResultDto(cfg.name, SystemTemplateInstanceStatus.ERROR,
                        "Failed to validate instance, first message: " + validation.get(0).message);
            }
        } catch (Exception e) {
            log.warn("Exception validating instance {} created from system template", cfg.name, e);
            return new SystemTemplateInstanceResultDto(cfg.name, SystemTemplateInstanceStatus.ERROR,
                    "Cannot validate instance: " + e.toString());
        }

        try {
            ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNamedMaster(group).update(iud,
                    null);
        } catch (Exception e) {
            log.warn("Cannot create instance {} for system template.", cfg.name, e);
            return new SystemTemplateInstanceResultDto(cfg.name, SystemTemplateInstanceStatus.ERROR,
                    "Cannot create instance: " + e.toString());
        }

        return new SystemTemplateInstanceResultDto(cfg.name, SystemTemplateInstanceStatus.OK,
                "Successfully created instance with ID " + cfg.id);
    }

    private List<InstanceNodeConfigurationDto> createInstanceNodesFromTemplate(InstanceConfiguration config, SystemManifest smf,
            FlattenedInstanceTemplateConfiguration tpl, SystemTemplateGroupMapping mappings, List<ApplicationManifest> apps,
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

        for (var group : tpl.groups) {
            String mappedToNode = mappings.groupToNode.get(group.name);
            if (mappedToNode == null || mappedToNode.isBlank()) {
                continue; // nope;
            }

            InstanceNodeConfigurationDto node = result.stream().filter(n -> n.nodeName.equals(mappedToNode)).findFirst()
                    .or(() -> {
                        var r = new InstanceNodeConfigurationDto(mappedToNode);

                        r.nodeConfiguration = new InstanceNodeConfiguration();
                        r.nodeConfiguration.copyRedundantFields(config);

                        // NO need to care about (redundant!) instance variables, etc. This is done when saving the instance.

                        r.nodeConfiguration.controlGroups.addAll(createControlGroupsFromTemplate(tpl, mappings, ttor));

                        result.add(r);
                        return Optional.of(r);
                    }).get();

            if (group.type == ApplicationType.CLIENT) {
                createApplicationsForClientGroup(node, group, apps, ttor, appFilter, globalLookup);
            } else {
                createApplicationsForServerGroup(node, group, apps, ttor, nodeStates.get(node.nodeName), appFilter, globalLookup);
            }
        }

        return result;
    }

    private void createApplicationsForClientGroup(InstanceNodeConfigurationDto node,
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

    private void createApplicationsForServerGroup(InstanceNodeConfigurationDto node,
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

    private ApplicationConfiguration createApplicationFromTemplate(FlattenedApplicationTemplateConfiguration reqApp,
            ApplicationManifest am, InstanceNodeConfigurationDto node, TrackingTemplateOverrideResolver ttor,
            Function<String, LinkedValueConfiguration> globalLookup) {
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        ApplicationDescriptor appDesc = am.getDescriptor();

        cfg.id = UuidHelper.randomId();
        cfg.name = TemplateHelper.process(reqApp.name, ttor, ttor::canResolve);
        cfg.application = am.getKey();
        cfg.pooling = appDesc.pooling;
        cfg.processControl = new ProcessControlConfiguration();

        {
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

    private CommandConfiguration createCommand(ExecutableDescriptor command, List<TemplateParameter> tplParameters,
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

    private List<ProcessControlGroupConfiguration> createControlGroupsFromTemplate(FlattenedInstanceTemplateConfiguration tpl,
            SystemTemplateGroupMapping mappings, TrackingTemplateOverrideResolver ttor) {
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

    private List<VariableConfiguration> createInstanceVariablesFromTemplate(FlattenedInstanceTemplateConfiguration tpl,
            TrackingTemplateOverrideResolver ttor) {

        List<VariableConfiguration> result = new ArrayList<>();

        for (var v : tpl.instanceVariables) {
            v.value = new LinkedValueConfiguration(TemplateHelper.process(v.value.getPreRenderable(), ttor, ttor::canResolve));
            result.add(v);
        }

        return result;
    }

    private Manifest.Key createSystemFromTemplateRequest(SystemTemplateRequestDto request,
            TrackingTemplateOverrideResolver ttor) {
        SystemConfigurationDto scd = new SystemConfigurationDto();
        scd.config = new SystemConfiguration();
        scd.minion = request.minion;
        scd.config.id = UuidHelper.randomId();
        scd.config.name = TemplateHelper.process(request.name, ttor, ttor::canResolve);
        scd.config.description = TemplateHelper.process(request.template.description, ttor, ttor::canResolve);

        for (var v : request.template.systemVariables) {
            // expand template variables inline for each system variable.
            v.value = new LinkedValueConfiguration(TemplateHelper.process(v.value.getPreRenderable(), ttor, ttor::canResolve));
            scd.config.systemVariables.add(v);
        }

        return update(scd);
    }

}
