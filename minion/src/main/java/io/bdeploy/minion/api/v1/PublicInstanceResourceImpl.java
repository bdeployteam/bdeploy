package io.bdeploy.minion.api.v1;

import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicProxyResource;
import io.bdeploy.api.remote.v1.dto.EndpointsConfigurationApi;
import io.bdeploy.api.remote.v1.dto.HttpEndpointApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi.InstancePurposeApi;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.minion.remote.jersey.CommonRootResourceImpl;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class PublicInstanceResourceImpl implements PublicInstanceResource {

    @Context
    private ResourceContext rc;

    @Context
    private UriInfo ui;

    private final String groupName;

    public PublicInstanceResourceImpl(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public SortedMap<Manifest.Key, InstanceConfigurationApi> listInstanceConfigurations(boolean latestOnly) {
        SortedMap<Manifest.Key, InstanceConfigurationApi> result = new TreeMap<>();

        for (Map.Entry<Manifest.Key, InstanceConfiguration> ic : rc.getResource(CommonRootResourceImpl.class)
                .getInstanceResource(groupName).listInstanceConfigurations(latestOnly).entrySet()) {

            InstanceConfigurationApi ica = new InstanceConfigurationApi();
            ica.uuid = ic.getValue().id;
            ica.name = ic.getValue().name;
            ica.description = ic.getValue().description;
            if (ic.getValue().purpose != null) {
                ica.purpose = InstancePurposeApi.valueOf(ic.getValue().purpose.name());
            }
            ica.product = ic.getValue().product;

            result.put(ic.getKey(), ica);
        }

        return result;
    }

    @Override
    public SortedMap<String, EndpointsConfigurationApi> getAllEndpoints(String instanceId) {
        SortedMap<String, EndpointsConfigurationApi> result = new TreeMap<>();

        for (Map.Entry<String, EndpointsConfiguration> ec : rc.getResource(CommonRootResourceImpl.class)
                .getInstanceResource(groupName).getAllEndpoints(instanceId).entrySet()) {

            EndpointsConfigurationApi eca = new EndpointsConfigurationApi();

            if (ec.getValue().http != null) {
                eca.http = new ArrayList<>();
                for (HttpEndpoint he : ec.getValue().http) {
                    HttpEndpointApi hea = new HttpEndpointApi();

                    hea.id = he.id;
                    hea.path = he.path.getPreRenderable();

                    eca.http.add(hea);
                }
            }

            result.put(ec.getKey(), eca);
        }

        return result;
    }

    @Override
    public PublicProxyResource getProxyResource(String instanceId, String applicationId) {
        // this one is not explicitly implemented. the reason is that the proxy resource /must/ simply be a resource
        // implementation that forwards every request with every supported method. by /not/ implementing it in the public
        // API, we will be able to accept additional methods supported in future releases of BDeploy without adaption.
        String uriString = ui.getRequestUri().toString().replace("/public/v1/common/proxy", "/master/common/proxy");
        Response response = Response.temporaryRedirect(UriBuilder.fromUri(uriString).build()).build();
        throw new WebApplicationException(response);
    }
}
