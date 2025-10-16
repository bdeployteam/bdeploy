package io.bdeploy.ui.api.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.api.SoftwareResource;
import io.bdeploy.ui.dto.LatestProductVersionRequestDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductKeyWithSourceDto;
import io.bdeploy.ui.utils.ProductVersionMatchHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class SoftwareRepositoryResourceImpl implements SoftwareRepositoryResource {

    @Context
    private ResourceContext rc;

    @Context
    private ContainerRequestContext crq;

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Inject
    private AuthService auth;

    @Inject
    private AuthGroupService authGroup;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private VersionSorterService vss;

    @Override
    public List<SoftwareRepositoryConfiguration> list() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg == null || !isAuthorized(new ScopedPermission(cfg.name, Permission.READ))) {
                continue;
            }
            result.add(cfg);
        }
        return result;
    }

    private boolean isAuthorized(ScopedPermission requiredPermission) {
        // need to obtain from request to avoid SecurityContextInjectee wrapper.
        SecurityContext ctx = crq.getSecurityContext();
        if (!(ctx instanceof JerseySecurityContext)) {
            return false;
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) ctx;

        return securityContext.isAuthorized(requiredPermission)
                || auth.isAuthorized(context.getUserPrincipal().getName(), requiredPermission);
    }

    @Override
    public SoftwareResource getSoftwareResource(String softwareRepository) {
        return rc.initResource(new SoftwareResourceImpl(getSoftwareRepositoryHive(softwareRepository), softwareRepository));
    }

    @Override
    public ProductResource getProductResource(String softwareRepository) {
        return rc.initResource(new ProductResourceImpl(getSoftwareRepositoryHive(softwareRepository), softwareRepository));
    }

    private BHive getSoftwareRepositoryHive(String softwareRepository) {
        BHive hive = registry.get(softwareRepository);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public void create(SoftwareRepositoryConfiguration config) {
        if (config.name == null || config.name.contains("..")) {
            // trying to escape the storage path.
            throw new WebApplicationException("Invalid name: " + config.name, Status.BAD_REQUEST);
        }

        // TODO: better storage location selection mechanism in the future.
        Path storage = registry.getLocations().iterator().next();
        Path hive = storage.resolve(config.name);

        if (Files.isDirectory(hive)) {
            throw new WebApplicationException(
                    "Instance Group or Software Repository with the name " + config.name + " already exists.",
                    Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), RollingFileAuditor.getFactory().apply(hive), registry.getActivityReporter());
        Path defaultPool = minion.getDefaultPoolPath();
        if (defaultPool != null) {
            h.enablePooling(defaultPool, false);
        }
        registry.register(config.name, h);
        SoftwareRepositoryManifest srm = new SoftwareRepositoryManifest(h);
        srm.update(config);
    }

    private BHive getRepoHive(String repo) {
        BHive hive = registry.get(repo);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public SoftwareRepositoryConfiguration read(String repo) {
        return new SoftwareRepositoryManifest(getRepoHive(repo)).read();
    }

    @Override
    public void update(String repo, SoftwareRepositoryConfiguration config) {
        RuntimeAssert.assertEquals(repo, config.name, "Repository update changes repository name");
        new SoftwareRepositoryManifest(getRepoHive(repo)).update(config);
    }

    @Override
    public void delete(String repo) {
        BHive bHive = registry.get(repo);
        if (bHive == null) {
            throw new WebApplicationException("Repository '" + repo + "' does not exist");
        }

        Manifest.Key latestKey = new SoftwareRepositoryManifest(bHive).getKey();

        auth.removePermissions(repo);
        registry.unregister(repo);
        PathHelper.deleteRecursiveRetry(Paths.get(bHive.getUri()));
        changes.remove(ObjectChangeType.SOFTWARE_REPO, latestKey);
    }

    @Override
    public SortedSet<UserInfo> getAllUser(String repo) {
        BHive bHive = registry.get(repo);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + repo + "' does not exist");
        }
        return auth.getAll();
    }

    @Override
    public SortedSet<UserGroupInfo> getUserGroups(String repo) {
        BHive bHive = registry.get(repo);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + repo + "' does not exist");
        }
        return authGroup.getAll();
    }

    @Override
    public void updateUserPermissions(String repo, UserPermissionUpdateDto[] permissions) {
        auth.updatePermissions(repo, permissions);
        Manifest.Key key = new SoftwareRepositoryManifest(registry.get(repo)).getKey();
        for (var perm : permissions) {
            changes.change(ObjectChangeType.USER, key, Map.of(ObjectChangeDetails.USER_NAME, perm.user));
        }
    }

    @Override
    public void updateUserGroupPermissions(String repo, UserGroupPermissionUpdateDto[] permissions) {
        authGroup.updatePermissions(repo, permissions);
        Manifest.Key key = new SoftwareRepositoryManifest(registry.get(repo)).getKey();
        for (var perm : permissions) {
            changes.change(ObjectChangeType.USER_GROUP, key, Map.of(ObjectChangeDetails.USER_GROUP_ID, perm.group));
        }
    }

    @Override
    public ProductKeyWithSourceDto getLatestProductVersion(LatestProductVersionRequestDto req) {
        Comparator<Manifest.Key> comparator = null;

        List<String> repos = req.groupOrRepo != null ? List.of(req.groupOrRepo) : list().stream().map(r -> r.name).toList();

        List<ProductKeyWithSourceDto> versions = new ArrayList<>();

        for (String repo : repos) {
            // ProductResource.list(key) already returns sorted list (latest version first), so we only need to filter and grab first
            ProductDto repoLatestProduct = getProductResource(repo).list(req.key).stream()
                    .filter(dto -> req.productId == null || req.productId.isBlank() || req.productId.equals(dto.product))
                    .filter(dto -> ProductVersionMatchHelper.matchesVersion(dto, req.version, req.regex))
                    .filter(dto -> req.instanceTemplate == null
                            || dto.instanceTemplates.stream().anyMatch(it -> req.instanceTemplate.equals(it.name)))
                    .findFirst().orElse(null);

            if (repoLatestProduct == null) {
                continue;
            }

            Comparator<Manifest.Key> repoComparator = vss.getKeyComparator(repo, repoLatestProduct.key);
            if (comparator == null) {
                comparator = repoComparator;
            } else if (!comparator.getClass().equals(repoComparator.getClass())) {
                throw new WebApplicationException("Cannot determine latest product version. Found different comparators: "
                        + comparator.getClass().getName() + " and " + repoComparator.getClass().getName(), Status.BAD_REQUEST);
            }
            versions.add(new ProductKeyWithSourceDto(repo, repoLatestProduct.key));
        }

        if (versions.isEmpty()) {
            throw new WebApplicationException("No product versions found for --key=" + req.key + " --productId=" + req.productId
                    + " --version=" + req.version + " --repo=" + req.groupOrRepo + " --regex=" + req.regex
                    + " --instanceTemplate=" + req.instanceTemplate, Status.NOT_FOUND);
        }

        if (comparator == null) {
            throw new WebApplicationException("Cannot determine latest product version. Found no comparator for --key= " + req.key
                    + " --productId=" + req.productId + " --version=" + req.version + " --repo=" + req.groupOrRepo + " --regex="
                    + req.regex + " --instanceTemplate=" + req.instanceTemplate, Status.NOT_FOUND);
        }

        // cannot use comparator in lambda (not effectively final)
        Comparator<Manifest.Key> finalComparator = comparator;
        versions.sort((a, b) -> finalComparator.compare(a.key, b.key));
        return versions.get(0);
    }

}
