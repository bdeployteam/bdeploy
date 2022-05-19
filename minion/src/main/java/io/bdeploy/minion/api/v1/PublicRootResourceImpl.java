package io.bdeploy.minion.api.v1;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.minion.remote.jersey.CommonRootResourceImpl;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.impl.AuthResourceImpl;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

/**
 * V1 implementation of the public API.
 */
public class PublicRootResourceImpl implements PublicRootResource {

    @Context
    private ResourceContext rc;

    @Context
    private UriInfo ui;

    @Override
    public String getVersion() {
        return rc.getResource(CommonRootResourceImpl.class).getVersion().toString();
    }

    @Override
    public Response login(String user, String pass, boolean full) {
        AuthResource auth = rc.getResource(AuthResourceImpl.class);
        if (full) {
            return auth.authenticatePacked(new CredentialsApi(user, pass));
        } else {
            return auth.authenticate(new CredentialsApi(user, pass));
        }
    }

    @Override
    public Response login2(CredentialsApi credentials, boolean full) {
        AuthResource auth = rc.getResource(AuthResourceImpl.class);
        if (full) {
            return auth.authenticatePacked(credentials);
        } else {
            return auth.authenticate(credentials);
        }
    }

    @Override
    public List<SoftwareRepositoryConfigurationApi> getSoftwareRepositories() {
        List<SoftwareRepositoryConfigurationApi> result = new ArrayList<>();
        for (SoftwareRepositoryConfiguration src : rc.getResource(CommonRootResourceImpl.class).getSoftwareRepositories()) {
            SoftwareRepositoryConfigurationApi srca = new SoftwareRepositoryConfigurationApi();

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

            igca.name = igc.name;
            igca.title = igc.title;
            igca.description = igc.description;

            result.add(igca);
        }

        return result;
    }

    @Override
    public InstanceGroupConfigurationApi getInstanceGroupByInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new WebApplicationException("No instance group parameter given", Status.BAD_REQUEST);
        }

        for (InstanceGroupConfigurationApi instanceGroup : this.getInstanceGroups()) {

            boolean containsInstance = getInstanceResource(instanceGroup.name).listInstanceConfigurations(false).values().stream()
                    .map(instance -> instance.uuid).filter(uuid -> instanceId.equals(uuid)).findAny().isPresent();

            if (containsInstance) {
                return instanceGroup;
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
