package io.bdeploy.minion.remote.jersey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.ui.api.AuthService;

public class CommonRootResourceImpl implements CommonRootResource {

    private static final Logger log = LoggerFactory.getLogger(CommonRootResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Context
    private ContainerRequestContext context;

    @Override
    public Version getVersion() {
        return VersionHelper.getVersion();
    }

    @Override
    public List<SoftwareRepositoryConfiguration> getSoftwareRepositories() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public void addSoftwareRepository(SoftwareRepositoryConfiguration config, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: {}, valid are: {}", storage, getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, config.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new SoftwareRepositoryManifest(h).update(config);
        registry.register(config.name, h);
    }

    @Override
    public Set<String> getStorageLocations() {
        return registry.getLocations().stream().map(Path::toString).collect(Collectors.toSet());
    }

    @Override
    public List<InstanceGroupConfiguration> getInstanceGroups() {
        // need to obtain from request to avoid SecurityContextInjectee wrapper.
        SecurityContext ctx = context.getSecurityContext();
        if (!(ctx instanceof JerseySecurityContext)) {
            throw new ForbiddenException(
                    "User '" + ctx.getUserPrincipal().getName() + "' is not authorized to access requested resource.");
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) ctx;

        List<InstanceGroupConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            InstanceGroupConfiguration cfg = new InstanceGroupManifest(entry.getValue()).read();
            if (cfg == null) {
                continue;
            }
            // The current user must have at least scoped read permissions
            ScopedPermission requiredPermission = new ScopedPermission(cfg.name, Permission.READ);
            if (!securityContext.isAuthorized(requiredPermission)
                    && !auth.isAuthorized(securityContext.getUserPrincipal().getName(), requiredPermission)) {
                continue;
            }

            result.add(cfg);
        }
        return result;
    }

    @Override
    public void addInstanceGroup(InstanceGroupConfiguration meta, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: {}, valid are: {}", storage, getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, meta.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new InstanceGroupManifest(h).update(meta);
        registry.register(meta.name, h);
    }

    @Override
    public void deleteInstanceGroup(String name) {
        BHive bHive = registry.get(name);
        if (bHive == null) {
            throw new WebApplicationException("Instance Group '" + name + "' does not exist", Status.NOT_FOUND);
        }
        registry.unregister(name);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
    }

    @Override
    public CommonInstanceResource getInstanceResource(String name) {
        if (name == null) {
            throw new WebApplicationException("No instance group parameter given", Status.BAD_REQUEST);
        }
        BHive bHive = registry.get(name);
        if (bHive == null) {
            throw new WebApplicationException("Instance Group '" + name + "' does not exist", Status.NOT_FOUND);
        }
        return rc.initResource(new CommonInstanceResourceImpl(name, bHive));
    }

}
