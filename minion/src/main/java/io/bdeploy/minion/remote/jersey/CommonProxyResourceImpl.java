package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Collectors;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.remote.CommonProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;

public class CommonProxyResourceImpl implements CommonProxyResource {

    @Context
    private ContainerRequestContext request;

    @Context
    private UriInfo info;

    private final String group;
    private final String instanceId;
    private final String applicationId;

    private final SortedMap<String, EndpointsConfiguration> endpoints;

    private final ProxyForwarder forwarder;

    public CommonProxyResourceImpl(String group, String instanceId, String applicationId,
            SortedMap<String, EndpointsConfiguration> endpoints, ProxyForwarder forwarder) {
        this.group = group;
        this.instanceId = instanceId;
        this.applicationId = applicationId;
        this.endpoints = endpoints;
        this.forwarder = forwarder;
    }

    private HttpEndpoint getEndpoint(String endpointId) {
        Optional<HttpEndpoint> ep = endpoints.entrySet().stream().flatMap(e -> e.getValue().http.stream())
                .filter(e -> e.id.equals(endpointId)).findFirst();

        if (!ep.isPresent()) {
            throw new WebApplicationException("Endpoint " + endpointId + " not found for instance " + instanceId,
                    Status.PRECONDITION_FAILED);
        }

        return ep.get();
    }

    private ProxiedRequestWrapper wrap(String endpointId, byte[] body, String method) {
        return new ProxiedRequestWrapper(group, instanceId, applicationId, method, getEndpoint(endpointId), request.getHeaders(),
                filterBDeployParameters(info.getQueryParameters()), (body == null ? null : Base64.encodeBase64String(body)),
                request.getMediaType() == null ? null : request.getMediaType().toString());
    }

    private Map<String, List<String>> filterBDeployParameters(MultivaluedMap<String, String> queryParameters) {
        return queryParameters.entrySet().stream().filter(e -> !e.getKey().startsWith("BDeploy_"))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private Response unwrap(ProxiedResponseWrapper wrapper) {
        ResponseBuilder builder = Response.status(wrapper.responseCode, wrapper.responseReason);
        if (wrapper.base64body != null) {
            // no need to set the type, it's in the headers.
            builder.entity(Base64.decodeBase64(wrapper.base64body));
        }
        for (Map.Entry<String, List<String>> entry : wrapper.headers.entrySet()) {
            for (String value : entry.getValue()) {
                builder.header(entry.getKey(), value);
            }
        }
        return builder.build();
    }

    @Override
    public Response head(String endpointId) {
        return unwrap(forwarder.forward(wrap(endpointId, null, HttpMethod.HEAD)));
    }

    @Override
    public Response options(String endpointId) {
        return unwrap(forwarder.forward(wrap(endpointId, null, HttpMethod.OPTIONS)));
    }

    @Override
    public Response get(String endpointId) {
        return unwrap(forwarder.forward(wrap(endpointId, null, HttpMethod.GET)));
    }

    @Override
    public Response put(String endpointId, byte[] body) {
        return unwrap(forwarder.forward(wrap(endpointId, body, HttpMethod.PUT)));
    }

    @Override
    public Response post(String endpointId, byte[] body) {
        return unwrap(forwarder.forward(wrap(endpointId, body, HttpMethod.POST)));
    }

    @Override
    public Response delete(String endpointId) {
        return unwrap(forwarder.forward(wrap(endpointId, null, HttpMethod.DELETE)));
    }

    @Override
    public Response patch(String endpointId, byte[] body) {
        return unwrap(forwarder.forward(wrap(endpointId, body, HttpMethod.PATCH)));
    }

}
