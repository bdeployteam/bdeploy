package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpEndpointType;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.settings.CustomDataGrouping;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.dto.ClientApplicationDto;
import io.bdeploy.ui.dto.InstanceClientAppsDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceUiEndpointsDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.UiEndpointDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class InstanceGroupResourceImpl implements InstanceGroupResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceGroupResourceImpl.class);
    private static final String CACHE_AGGRESSIVE = "public, max-age=31536000";

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Inject
    private PluginManager pm;

    @Context
    private SecurityContext context;

    @Inject
    private Minion minion;

    @Inject
    private ChangeEventManager changes;

    @Override
    public List<InstanceGroupConfigurationDto> list() {
        List<InstanceGroupConfigurationDto> result = new ArrayList<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfigurationDto instanceGroupConfigurationDto = getInstanceGroupConfigurationDto(hive);

            if (instanceGroupConfigurationDto != null) {
                result.add(instanceGroupConfigurationDto);
            }
        }
        return result;
    }

    private InstanceGroupConfigurationDto getInstanceGroupConfigurationDto(BHive hive) {
        InstanceGroupConfiguration cfg = new InstanceGroupManifest(hive).read();

        if (cfg == null) {
            return null;
        }

        // The current user must have at least scoped client download permissions
        ScopedPermission requiredPermission = new ScopedPermission(cfg.name, Permission.CLIENT);
        if (!auth.isAuthorized(context.getUserPrincipal().getName(), requiredPermission)) {
            return null;
        }

        // Fetch instance group's instance IDs and add them to searchable text
        List<String> instanceIds = new ArrayList<>();
        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        for (Key imKey : imKeys) {
            InstanceManifest im = InstanceManifest.of(hive, imKey);
            InstanceConfiguration config = im.getConfiguration();
            instanceIds.add(config.id);
        }

        return new InstanceGroupConfigurationDto(cfg, String.join(" ", instanceIds));
    }

    @Override
    public void create(InstanceGroupConfiguration config) {
        // TODO: better storage location selection mechanism in the future.
        Path storage = registry.getLocations().iterator().next();
        Path hive = storage.resolve(config.name);

        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists: ", Status.NOT_ACCEPTABLE);
        }

        // update the managed flag - indicator required when switching modes
        if (minion.getMode() == MinionMode.STANDALONE) {
            config.managed = false;
        } else {
            config.managed = true;
        }

        try {
            BHive h = new BHive(hive.toUri(), RollingFileAuditor.getFactory().apply(hive), reporter);
            registry.register(config.name, h);
            InstanceGroupManifest igm = new InstanceGroupManifest(h);
            igm.update(config);
        } catch (Exception e) {
            PathHelper.deleteRecursive(hive);
            throw e;
        }
    }

    @Override
    public InstanceGroupConfigurationDto getInstanceGroupConfigurationDto(String group) {
        BHive hive = getGroupHive(group);
        return getInstanceGroupConfigurationDto(hive);
    }

    private BHive getGroupHive(String group) {
        BHive hive = registry.get(group);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    private InstanceGroupConfiguration read(String group) {
        return new InstanceGroupManifest(getGroupHive(group)).read();
    }

    @Override
    public void update(String group, InstanceGroupConfiguration config) {
        RuntimeAssert.assertEquals(group, config.name, "Group update changes group name");
        InstanceGroupManifest igm = new InstanceGroupManifest(getGroupHive(group));
        igm.update(config);

        // the rest may run a while in the background and propagate changes to each server where possible.
        if (minion.getMode() == MinionMode.CENTRAL) {
            // update all managed servers, user had to confirm this in web UI.
            ManagedServersResource ms = rc.initResource(new ManagedServersResourceImpl());
            List<ManagedMasterDto> servers = ms.getManagedServers(group);

            for (ManagedMasterDto dto : servers) {
                try {
                    ms.synchronize(group, dto.hostName);
                } catch (Exception e) {
                    log.warn("Cannot synchronize with {} on {}", dto.hostName, group, e);
                }
            }
        }
    }

    @Override
    public void updatePermissions(String group, UserPermissionUpdateDto[] permissions) {
        auth.updatePermissions(group, permissions);
        Manifest.Key key = new InstanceGroupManifest(registry.get(group)).getKey();
        for (var perm : permissions) {
            changes.change(ObjectChangeType.USER, key, Map.of(ObjectChangeDetails.USER_NAME, perm.user));
        }
    }

    @Override
    public void delete(String group) {
        BHive bHive = registry.get(group);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + group + "' does not exist");
        }

        // unload all product-plugins
        for (Manifest.Key key : ProductManifest.scan(bHive)) {
            pm.unloadProduct(key);
        }

        InstanceGroupManifest igm = new InstanceGroupManifest(bHive);
        Manifest.Key latestKey = igm.getKey();

        auth.removePermissions(group);
        registry.unregister(group);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
        changes.remove(ObjectChangeType.INSTANCE_GROUP, latestKey);
    }

    @Override
    public SortedSet<UserInfo> getAllUser(String group) {
        BHive bHive = registry.get(group);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + group + "' does not exist");
        }
        return auth.getAll();
    }

    @Override
    public void updateImage(String group, InputStream imageData) {
        InstanceGroupConfiguration config = read(group);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Transaction t = getGroupHive(group).getTransactions().begin()) {
            ByteStreams.copy(imageData, baos);
            ObjectId id = getGroupHive(group).execute(new ImportObjectOperation().setData(baos.toByteArray()));
            config.logo = id;
            update(group, config);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read image", e);
        }
    }

    @Override
    public void deleteImage(String group) {
        InstanceGroupConfiguration config = read(group);
        config.logo = null;
        update(group, config);
    }

    @Override
    public Response readImage(String group) {
        ObjectId id = read(group).logo;
        if (id == null) {
            return Response.ok().build();
        }
        return Response.ok(getGroupHive(group).execute(new ObjectLoadOperation().setObject(id)))
                .header("Cache-Control", CACHE_AGGRESSIVE).build();
    }

    @Override
    public String createId(String group) {
        // TODO: actually assure that the ID is unique for the use in instance and application IDs.
        return UuidHelper.randomId();
    }

    @Override
    public Collection<InstanceClientAppsDto> listClientApps(String group, OperatingSystem os) {
        Collection<InstanceClientAppsDto> result = new ArrayList<>();

        BHive hive = getGroupHive(group);
        InstanceResource resource = getInstanceResource(group);
        for (InstanceDto idto : resource.list()) {
            String instanceId = idto.instanceConfiguration.id;

            // Always use latest version to lookup remote service
            InstanceManifest im = InstanceManifest.load(hive, instanceId, null);

            // Contact master to find out the active version. Skip if no version is active
            String active = getInstanceResource(group).getDeploymentStates(instanceId).activeTag;
            if (active == null) {
                continue;
            }

            // Get a list of all node manifests - clients are stored in a special node
            if (!active.equals(im.getManifest().getTag())) {
                // make sure we do have the active version.
                im = InstanceManifest.load(hive, instanceId, active);
            }
            SortedMap<String, Key> manifests = im.getInstanceNodeManifests();
            Key clientKey = manifests.get(InstanceManifest.CLIENT_NODE_NAME);
            if (clientKey == null) {
                continue;
            }
            InstanceClientAppsDto clientApps = new InstanceClientAppsDto();
            clientApps.instance = idto.instanceConfiguration;
            clientApps.applications = new ArrayList<>();

            // Add all configured client applications
            InstanceNodeManifest instanceNode = InstanceNodeManifest.of(hive, clientKey);
            for (ApplicationConfiguration appConfig : instanceNode.getConfiguration().applications) {
                ClientApplicationDto clientApp = new ClientApplicationDto();
                clientApp.id = appConfig.id;
                clientApp.description = appConfig.name;
                ScopedManifestKey scopedKey = ScopedManifestKey.parse(appConfig.application);
                clientApp.os = scopedKey.getOperatingSystem();
                if (os != null && clientApp.os != os) {
                    continue;
                }
                clientApps.applications.add(clientApp);
            }

            // Only add if we have at least one application
            if (clientApps.applications.isEmpty()) {
                continue;
            }
            result.add(clientApps);
        }
        return result;
    }

    @Override
    public Collection<InstanceUiEndpointsDto> listUiEndpoints(String group) {
        Collection<InstanceUiEndpointsDto> result = new ArrayList<>();

        BHive hive = getGroupHive(group);
        InstanceResource resource = getInstanceResource(group);
        for (InstanceDto idto : resource.list()) {
            String instanceId = idto.instanceConfiguration.id;

            // Always use latest version to lookup remote service
            InstanceManifest im = InstanceManifest.load(hive, instanceId, null);

            // Contact master to find out the active version. Skip if no version is active
            String active = getInstanceResource(group).getDeploymentStates(instanceId).activeTag;
            if (active == null) {
                continue;
            }

            // Get a list of all node manifests - clients are stored in a special node
            if (!active.equals(im.getManifest().getTag())) {
                // make sure we do have the active version.
                im = InstanceManifest.load(hive, instanceId, active);
            }
            SortedMap<String, Key> manifests = im.getInstanceNodeManifests();

            InstanceUiEndpointsDto allInstEps = new InstanceUiEndpointsDto();
            allInstEps.instance = idto.instanceConfiguration;
            allInstEps.endpoints = new ArrayList<>();

            for (Map.Entry<String, Key> nodeEntry : manifests.entrySet()) {
                // Add all configured client applications
                InstanceNodeManifest instanceNode = InstanceNodeManifest.of(hive, nodeEntry.getValue());
                for (ApplicationConfiguration appConfig : instanceNode.getConfiguration().applications) {
                    for (HttpEndpoint configuredEp : appConfig.endpoints.http) {
                        if (configuredEp.type != HttpEndpointType.UI) {
                            continue;
                        }

                        UiEndpointDto uiEp = new UiEndpointDto();
                        uiEp.id = appConfig.id;
                        uiEp.appName = appConfig.name;
                        uiEp.endpoint = configuredEp;
                        allInstEps.endpoints.add(uiEp);
                    }
                }

                // Only add if we have at least one application
                if (allInstEps.endpoints.isEmpty()) {
                    continue;
                }
                result.add(allInstEps);
            }
        }
        return result;
    }

    @Override
    public InstanceResource getInstanceResource(String group) {
        BHive groupHive = getGroupHive(group);

        // check if the current instance group mode matches the server mode, else don't continue.
        boolean isManagedGroup = read(group).managed;
        boolean requiresManagedGroup = minion.getMode() != MinionMode.STANDALONE;
        if (requiresManagedGroup != isManagedGroup) {
            log.info("Rejecting request to access {} instance group on {} server", (isManagedGroup ? "managed" : "standalone"),
                    minion.getMode());
            throw new WebApplicationException("Instance group mode does not match server mode.", Status.SERVICE_UNAVAILABLE);
        }

        return rc.initResource(new InstanceResourceImpl(group, groupHive));
    }

    @Override
    public SystemResource getSystemResource(String group) {
        BHive groupHive = getGroupHive(group);
        return rc.initResource(new SystemResourceImpl(group, groupHive));
    }

    @Override
    public ProductResource getProductResource(String group) {
        return rc.initResource(new ProductResourceImpl(getGroupHive(group), group));
    }

    @Override
    public Map<String, CustomAttributesRecord> listAttributes() {
        Map<String, CustomAttributesRecord> result = new HashMap<>();

        for (BHive groupHive : registry.getAll().values()) {
            InstanceGroupManifest manifest = new InstanceGroupManifest(groupHive);
            InstanceGroupConfiguration cfg = manifest.read();
            if (cfg == null) {
                continue;
            }
            // The current user must have at least scoped read permissions
            ScopedPermission requiredPermission = new ScopedPermission(cfg.name, Permission.READ);
            if (!auth.isAuthorized(context.getUserPrincipal().getName(), requiredPermission)) {
                continue;
            }
            result.put(cfg.name, manifest.getAttributes(groupHive).read());
        }
        return result;
    }

    @Override
    public CustomAttributesRecord getAttributes(String group) {
        BHive groupHive = getGroupHive(group);
        InstanceGroupManifest manifest = new InstanceGroupManifest(groupHive);
        if (manifest.getKey() == null) {
            throw new WebApplicationException("Cannot load " + group, Status.NOT_FOUND);
        }
        return manifest.getAttributes(groupHive).read();
    }

    @Override
    public void updateAttributes(String group, CustomAttributesRecord attributes) {
        BHive groupHive = getGroupHive(group);
        InstanceGroupManifest manifest = new InstanceGroupManifest(groupHive);
        Key key = manifest.getKey();
        if (key == null) {
            throw new WebApplicationException("Cannot load " + group, Status.NOT_FOUND);
        }
        manifest.getAttributes(groupHive).set(attributes);
        changes.change(ObjectChangeType.INSTANCE_GROUP, key,
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.ATTRIBUTES));
    }

    @Override
    public void updatePreset(String group, boolean multiple, List<CustomDataGrouping> preset) {
        BHive groupHive = getGroupHive(group);
        InstanceGroupManifest manifest = new InstanceGroupManifest(groupHive);
        Key key = manifest.getKey();
        if (key == null) {
            throw new WebApplicationException("Cannot load " + group, Status.NOT_FOUND);
        }
        InstanceGroupConfiguration igc = manifest.read();
        if (multiple) {
            igc.groupingMultiplePreset = preset;
        } else {
            igc.groupingSinglePreset = preset;
        }
        update(group, igc);
    }
}
