package io.bdeploy.ui.api.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.api.SoftwareResource;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;

public class SoftwareRepositoryResourceImpl implements SoftwareRepositoryResource {

    @Context
    private ResourceContext rc;

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

    @Override
    public List<SoftwareRepositoryConfiguration> list() {
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
        Manifest.Key key = new SoftwareRepositoryManifest(bHive).getKey();
        registry.unregister(repo);
        PathHelper.deleteRecursiveRetry(Paths.get(bHive.getUri()));
        changes.remove(ObjectChangeType.SOFTWARE_REPO, key);
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

}
