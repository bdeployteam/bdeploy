package io.bdeploy.minion.api.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.minion.remote.jersey.CommonRootResourceImpl;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.impl.AuthResourceImpl;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

/**
 * V1 implementation of the public API.
 */
public class PublicRootResourceImpl implements PublicRootResource {

    @Context
    private ResourceContext rc;

    @Context
    private UriInfo ui;

    @Context
    private ContainerRequestContext crq;

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private AuthService auth;

    @Override
    public String getVersion() {
        return rc.getResource(CommonRootResourceImpl.class).getVersion().toString();
    }

    /** @deprecated Because {@link PublicRootResource#login(String, String, boolean)} is deprecated. */
    @Deprecated(since = "2.3.0")
    @Override
    public Response login(String user, String pass, boolean full) {
        AuthResource authRes = rc.getResource(AuthResourceImpl.class);
        if (full) {
            return authRes.authenticatePacked(new CredentialsApi(user, pass));
        } else {
            return authRes.authenticate(new CredentialsApi(user, pass));
        }
    }

    @Override
    public Response login2(CredentialsApi credentials, boolean full) {
        AuthResource authRes = rc.getResource(AuthResourceImpl.class);
        if (full) {
            return authRes.authenticatePacked(credentials);
        } else {
            return authRes.authenticate(credentials);
        }
    }

    @Override
    public List<SoftwareRepositoryConfigurationApi> getSoftwareRepositories() {
        List<SoftwareRepositoryConfigurationApi> result = new ArrayList<>();
        for (SoftwareRepositoryConfiguration src : rc.getResource(CommonRootResourceImpl.class).getSoftwareRepositories()) {
            SoftwareRepositoryConfigurationApi srca = new SoftwareRepositoryConfigurationApi();

            if (src == null || !isAuthorized(new ScopedPermission(src.name, ScopedPermission.Permission.READ))) {
                continue;
            }

            srca.name = src.name;
            srca.description = src.description;

            result.add(srca);
        }
        return result;
    }

    @Override
    public List<InstanceGroupConfigurationApi> getInstanceGroups() {
        List<InstanceGroupConfigurationApi> result = new ArrayList<>();

        for (InstanceGroupConfiguration igc : rc.getResource(CommonRootResourceImpl.class).getInstanceGroups()) {
            InstanceGroupConfigurationApi igca = new InstanceGroupConfigurationApi();

            if (igc == null || !isAuthorized(new ScopedPermission(igc.name, ScopedPermission.Permission.READ))) {
                continue;
            }

            igca.name = igc.name;
            igca.title = igc.title;
            igca.description = igc.description;

            result.add(igca);
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
    public InstanceGroupConfigurationApi getInstanceGroupByInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new WebApplicationException("No instance group parameter given", Status.BAD_REQUEST);
        }

        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration cfg = new InstanceGroupManifest(hive).read();
            if (cfg == null) {
                continue;
            }

            SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
            for (Key imKey : imKeys) {
                InstanceManifest im = InstanceManifest.of(hive, imKey);
                InstanceConfiguration config = im.getConfiguration();
                if (instanceId.equals(config.id)) {
                    InstanceGroupConfigurationApi instanceGroup = new InstanceGroupConfigurationApi();
                    instanceGroup.name = cfg.name;
                    instanceGroup.title = cfg.title;
                    instanceGroup.description = cfg.description;
                    return instanceGroup;
                }
            }
        }

        throw new WebApplicationException("No instance group found for instance ID " + instanceId, Status.NOT_FOUND);
    }

    @Override
    public PublicInstanceResource getInstanceResource(String group) {
        if (group == null) {
            throw new WebApplicationException("No instance group parameter given", Status.BAD_REQUEST);
        }
        return rc.initResource(new PublicInstanceResourceImpl(group));
    }

}
