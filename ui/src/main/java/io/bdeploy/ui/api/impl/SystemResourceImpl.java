package io.bdeploy.ui.api.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.configuration.template.TrackingTemplateOverrideResolver;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.dto.SystemTemplateDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto;
import io.bdeploy.ui.dto.SystemTemplateResultDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class SystemResourceImpl implements SystemResource {

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
            remote = mp.getNamedMasterOrSelf(hive, target);
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
        RemoteService remote = mp.getNamedMasterOrSelf(hive, target);
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

            InstanceTemplateResourceImpl itr = rc.initResource(new InstanceTemplateResourceImpl(group, hive));

            for (InstanceTemplateReferenceDescriptor instance : template.instances) {
                ProductDto product = itr.findMatchingProductOrFail(instance, products);
                result.products.add(product);
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

        // we'll use the same remote as the system, so we can fetch it from there.
        RemoteService remote = mp.getControllingMaster(hive, key);

        // we'll use the shared logic from instance templates when applying system templates.
        InstanceTemplateResourceImpl itr = rc.initResource(new InstanceTemplateResourceImpl(group, hive));

        // 2. create each instance.
        for (var inst : request.template.instances) {
            String instName = TemplateHelper.process(inst.name, ttor, ttor::canResolve);
            var mappings = request.groupMappings.stream().filter(m -> m.instanceName.equals(instName)).findFirst();
            if (mappings.isEmpty()) {
                result.results.add(new InstanceTemplateReferenceResultDto(instName, InstanceTemplateReferenceStatus.ERROR,
                        "No group mappings."));
                continue;
            }

            if (mappings.get().groupToNode.isEmpty()) {
                result.results.add(new InstanceTemplateReferenceResultDto(instName, InstanceTemplateReferenceStatus.WARNING,
                        "Instance skipped."));
                continue;
            }

            List<TemplateVariableFixedValueOverride> perInstanceValues = mappings.get().templateVariableValues.entrySet().stream()
                    .map(e -> new TemplateVariableFixedValueOverride(e.getKey(), e.getValue())).toList();

            TrackingTemplateOverrideResolver ittor = new TrackingTemplateOverrideResolver(perInstanceValues, ttor);

            result.results.add(itr.createInstanceFromTemplateRequest(remote, key, inst, mappings.get().productKey,
                    mappings.get().groupToNode, ittor, request.purpose));
        }

        // 3. sync after we created everything.
        syncSystem(key);

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
