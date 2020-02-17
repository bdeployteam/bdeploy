package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.ScopedCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupPermissionDto;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.ClientApplicationDto;
import io.bdeploy.ui.dto.InstanceClientAppsDto;
import io.bdeploy.ui.dto.InstanceDto;

public class InstanceGroupResourceImpl implements InstanceGroupResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceGroupResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Context
    private SecurityContext context;

    @Inject
    private Minion minion;

    @Override
    public List<InstanceGroupConfiguration> list() {
        List<InstanceGroupConfiguration> result = new ArrayList<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration cfg = new InstanceGroupManifest(hive).read();
            if (cfg == null) {
                continue;
            }
            // The current user must have at least scoped read permissions
            ScopedCapability requiredCapability = new ScopedCapability(cfg.name, Capability.READ);
            if (!auth.isAuthorized(context.getUserPrincipal().getName(), requiredCapability)) {
                continue;
            }
            result.add(cfg);
        }
        return result;
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
            BHive h = new BHive(hive.toUri(), reporter);
            new InstanceGroupManifest(h).update(config);
            registry.register(config.name, h);

            auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), config.name);
        } catch (Exception e) {
            PathHelper.deleteRecursive(hive);
            throw e;
        }
    }

    private BHive getGroupHive(String group) {
        BHive hive = registry.get(group);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public InstanceGroupConfiguration read(String group) {
        return new InstanceGroupManifest(getGroupHive(group)).read();
    }

    @Override
    public void update(String group, InstanceGroupConfiguration config) {
        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), group);
        RuntimeAssert.assertEquals(group, config.name, "Group update changes group name");
        new InstanceGroupManifest(getGroupHive(group)).update(config);

        if (minion.getMode() == MinionMode.CENTRAL) {
            // update all managed servers, user had to confirm this in web UI.
            ManagedServersResource ms = rc.initResource(new ManagedServersResourceImpl());
            List<ManagedMasterDto> servers = ms.getManagedServers(group);

            for (ManagedMasterDto dto : servers) {
                try {
                    ms.synchronize(group, dto.hostName);
                } catch (Exception e) {
                    log.warn("Cannot synchronize with " + dto.hostName + " on " + group, e);
                }
            }
        }
    }

    @Override
    public void updatePermissions(String group, InstanceGroupPermissionDto[] permissions) {
        auth.updateInstanceGroupPermissions(group, permissions);
    }

    @Override
    public void delete(String group) {
        BHive bHive = registry.get(group);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + group + "' does not exist");
        }
        auth.removeInstanceGroupPermissions(group);
        registry.unregister(group);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
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
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ByteStreams.copy(imageData, baos);
            ObjectId id = getGroupHive(group).execute(new ImportObjectOperation().setData(baos.toByteArray()));
            config.logo = id;
            update(group, config);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read image", e);
        }
    }

    @Override
    public InputStream readImage(String group) {
        ObjectId id = read(group).logo;
        if (id == null) {
            return null;
        }
        return getGroupHive(group).execute(new ObjectLoadOperation().setObject(id));
    }

    @Override
    public String createUuid(String group) {
        // TODO: actually assure that the UUID is unique for the use in instance and application UUIDs.
        return UuidHelper.randomId();
    }

    @Override
    public Collection<InstanceClientAppsDto> listClientApps(String group, OperatingSystem os) {
        Collection<InstanceClientAppsDto> result = new ArrayList<>();

        BHive hive = getGroupHive(group);
        InstanceResource resource = getInstanceResource(group);
        for (InstanceDto idto : resource.list()) {
            String instanceId = idto.instanceConfiguration.uuid;

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
                clientApp.uuid = appConfig.uid;
                clientApp.description = appConfig.name;
                ScopedManifestKey scopedKey = ScopedManifestKey.parse(appConfig.application);
                clientApp.os = scopedKey.getOperatingSystem();
                if (clientApp.os != os) {
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
    public InstanceResource getInstanceResource(String group) {
        return rc.initResource(new InstanceResourceImpl(group, getGroupHive(group)));
    }

    @Override
    public ProductResource getProductResource(String group) {
        return rc.initResource(new ProductResourceImpl(getGroupHive(group)));
    }

}
