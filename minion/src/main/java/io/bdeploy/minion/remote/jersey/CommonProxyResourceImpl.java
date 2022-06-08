package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.remote.CommonProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper.ProxiedRequestCookie;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

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
    private final ProxyUnwrapper unwrapper;

    public CommonProxyResourceImpl(String group, String instanceId, String applicationId,
            SortedMap<String, EndpointsConfiguration> endpoints, ProxyForwarder forwarder, ProxyUnwrapper unwrapper) {
        this.group = group;
        this.instanceId = instanceId;
        this.applicationId = applicationId;
        this.endpoints = endpoints;
        this.forwarder = forwarder;
        this.unwrapper = unwrapper != null ? unwrapper : r -> r.defaultUnwrap();
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

    private ProxiedRequestWrapper wrap(String endpointId, String sub, byte[] body, String method) {
        Map<String, ProxiedRequestCookie> cookies = request.getCookies().entrySet().stream().filter(e -> !e.getKey().equals("st"))
                .collect(Collectors.toMap(Entry::getKey, e -> {
                    Cookie k = e.getValue();
                    return new ProxiedRequestWrapper.ProxiedRequestCookie(k.getName(), k.getValue(), k.getVersion(), k.getPath(),
                            k.getDomain());
                }));

        return new ProxiedRequestWrapper(group, instanceId, applicationId, method, getEndpoint(endpointId), sub,
                request.getHeaders(), filterBDeployParameters(info.getQueryParameters()),
                (body == null ? null : Base64.encodeBase64String(body)),
                request.getMediaType() == null ? null : request.getMediaType().toString(), cookies);
    }

    private Map<String, List<String>> filterBDeployParameters(MultivaluedMap<String, String> queryParameters) {
        return queryParameters.entrySet().stream().filter(e -> !e.getKey().startsWith("BDeploy_"))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public Response head(String endpointId, String sub) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, null, HttpMethod.HEAD)));
    }

    @Override
    public Response options(String endpointId, String sub) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, null, HttpMethod.OPTIONS)));
    }

    @Override
    public Response get(String endpointId, String sub) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, null, HttpMethod.GET)));
    }

    @Override
    public Response put(String endpointId, String sub, byte[] body) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, body, HttpMethod.PUT)));
    }

    @Override
    public Response post(String endpointId, String sub, byte[] body) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, body, HttpMethod.POST)));
    }

    @Override
    public Response delete(String endpointId, String sub) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, null, HttpMethod.DELETE)));
    }

    @Override
    public Response patch(String endpointId, String sub, byte[] body) {
        return unwrapper.unwrap(forwarder.forward(wrap(endpointId, sub, body, HttpMethod.PATCH)));
    }

}
