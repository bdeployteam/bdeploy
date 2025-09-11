package io.bdeploy.minion.remote.jersey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonProxyResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.jersey.errorpages.JerseyCustomErrorPages;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class CommonRootResourceImpl implements CommonRootResource {

    private static final Logger log = LoggerFactory.getLogger(CommonRootResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Context
    private ContainerRequestContext context;

    @Inject
    private MinionRoot minion;

    @Inject
    private NodeManager nodes;

    @Context
    private SecurityContext security;

    @Inject
    private ActionFactory af;

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

        BHive h = new BHive(hive.toUri(), RollingFileAuditor.getFactory().apply(hive), registry.getActivityReporter());
        Path defaultPool = minion.getDefaultPoolPath();
        if (defaultPool != null) {
            h.enablePooling(defaultPool, false);
        }
        registry.register(config.name, h);
        new SoftwareRepositoryManifest(h).update(config);
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

        meta.managed = (minion.getMode() != MinionMode.STANDALONE);

        BHive h = new BHive(hive.toUri(), RollingFileAuditor.getFactory().apply(hive), registry.getActivityReporter());

        Path defaultPool = minion.getDefaultPoolPath();
        if (defaultPool != null) {
            h.enablePooling(defaultPool, false);
        }

        registry.register(meta.name, h);
        new InstanceGroupManifest(h).update(meta);
    }

    @Override
    public void deleteInstanceGroup(String name) {
        try (ActionHandle h = af.run(Actions.DELETE_GROUP, name)) {
            BHive bHive = registry.get(name);
            if (bHive == null) {
                throw new WebApplicationException("Instance Group '" + name + "' does not exist", Status.NOT_FOUND);
            }
            registry.unregister(name);
            PathHelper.deleteRecursiveRetry(Paths.get(bHive.getUri()));
        }
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

    @Override
    public Path getLoggerConfig() {
        return minion.getLoggingConfigurationFile();
    }

    @Override
    public void setLoggerConfig(Path config) {
        Collection<String> nodeNames = nodes.getAllNodes().entrySet().stream()
                .filter(e -> e.getValue().minionNodeType != MinionDto.MinionNodeType.MULTI).map(e -> e.getKey()).toList();
        try {
            for (String nodeName : nodeNames) {
                try {
                    nodes.getNodeResourceIfOnlineOrThrow(nodeName, MinionStatusResource.class, security).setLoggerConfig(config);
                } catch (Exception e) {
                    log.error("Cannot udpate logging configuration on {}", nodeName, e);
                }
            }
        } finally {
            PathHelper.deleteRecursiveRetry(config);
        }
    }

    @Override
    public List<RemoteDirectory> getLogDirectories(String hive) {
        List<RemoteDirectory> result = new ArrayList<>();

        Collection<String> nodeNames = nodes.getAllNodes().entrySet().stream()
                .filter(e -> e.getValue().minionNodeType != MinionDto.MinionNodeType.MULTI).map(e -> e.getKey()).toList();
        for (String nodeName : nodeNames) {
            RemoteDirectory dir = new RemoteDirectory();
            dir.minion = nodeName;

            try {
                dir.entries.addAll(
                        nodes.getNodeResourceIfOnlineOrThrow(nodeName, MinionStatusResource.class, security).getLogEntries(hive));
            } catch (Exception e) {
                log.warn("Problem fetching log directory of {}: {}", nodeName, e.toString());
                dir.problem = e.toString();
            }

            result.add(dir);
        }

        return result;

    }

    @Override
    public EntryChunk getLogContent(String nodeName, RemoteDirectoryEntry entry, long offset, long limit) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, security).getEntryContent(entry,
                offset, limit);
    }

    @Override
    public Response getLogStream(String nodeName, RemoteDirectoryEntry entry) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, security).getEntryStream(entry);
    }

    @Override
    public CommonProxyResource getUiProxyResource(String group, String instance, String application) {
        BHive h = registry.get(group);
        if (h == null) {
            throw new WebApplicationException("Hive not found: " + group, Status.NOT_FOUND);
        }

        CommonInstanceResource ir = rc.initResource(new CommonInstanceResourceImpl(group, h));
        return rc.initResource(new CommonProxyResourceImpl(group, instance, application,
                ir.getAllEndpoints(instance).get(application), ir::forward, resp -> {
                    // cannot process our own 401, as we're rejected *very* early in the framework in case we're not authorized.
                    if (resp.responseCode == Response.Status.SERVICE_UNAVAILABLE.getStatusCode()) {
                        // application not running, and similar errors. we keep the original response but add a custom error page to it.
                        return Response.fromResponse(resp.defaultUnwrap())
                                .entity(JerseyCustomErrorPages.getErrorHtml(resp.responseCode, resp.responseReason)).build();
                    }
                    return resp.defaultUnwrap();
                }));
    }

}
