package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.remote.CommonProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper.ProxiedRequestCookie;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
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

    private final EndpointsConfiguration endpoints;

    private final Function<ProxiedRequestWrapper, ProxiedResponseWrapper> forwarder;
    private final Function<ProxiedResponseWrapper, Response> unwrapper;

    public CommonProxyResourceImpl(String group, String instanceId, String applicationId, EndpointsConfiguration endpoints,
            Function<ProxiedRequestWrapper, ProxiedResponseWrapper> forwarder,
            Function<ProxiedResponseWrapper, Response> unwrapper) {
        this.group = group;
        this.instanceId = instanceId;
        this.applicationId = applicationId;
        this.endpoints = endpoints;
        this.forwarder = forwarder;
        this.unwrapper = unwrapper != null ? unwrapper : ProxiedResponseWrapper::defaultUnwrap;
    }

    private HttpEndpoint getEndpoint(String fullPath) {
        if (endpoints == null) {
            throw new WebApplicationException("Endpoint " + fullPath + " not found for instance " + instanceId,
                    Status.SERVICE_UNAVAILABLE);
        }

        Optional<HttpEndpoint> ep = endpoints.http.stream().filter(e -> e.id.equals(fullPath) || fullPath.startsWith(e.id + '/'))
                .findFirst();

        if (!ep.isPresent()) {
            throw new WebApplicationException("Endpoint " + fullPath + " not found for instance " + instanceId,
                    Status.SERVICE_UNAVAILABLE);
        }

        return ep.get();
    }

    private ProxiedRequestWrapper wrap(String fullPath, byte[] body, String method) {
        Map<String, ProxiedRequestCookie> cookies = request.getCookies().entrySet().stream().filter(e -> !e.getKey().equals("st"))
                .collect(Collectors.toMap(Entry::getKey, e -> {
                    Cookie k = e.getValue();
                    return new ProxiedRequestWrapper.ProxiedRequestCookie(k.getName(), k.getValue(), k.getVersion(), k.getPath(),
                            k.getDomain());
                }));

        HttpEndpoint endpoint = getEndpoint(fullPath);
        String sub = getSubPath(endpoint, fullPath);

        return new ProxiedRequestWrapper(group, instanceId, applicationId, method, endpoint, sub, request.getHeaders(),
                filterBDeployParameters(info.getQueryParameters()), (body == null ? null : Base64.encodeBase64String(body)),
                request.getMediaType() == null ? null : request.getMediaType().toString(), cookies);
    }

    private String getSubPath(HttpEndpoint endpoint, String fullPath) {
        return fullPath.substring(endpoint.id.length());
    }

    private Map<String, List<String>> filterBDeployParameters(MultivaluedMap<String, String> queryParameters) {
        return queryParameters.entrySet().stream().filter(e -> !e.getKey().startsWith("BDeploy_"))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public Response head(String endpointId) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, null, HttpMethod.HEAD)));
    }

    @Override
    public Response options(String endpointId) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, null, HttpMethod.OPTIONS)));
    }

    @Override
    public Response get(String endpointId) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, null, HttpMethod.GET)));
    }

    @Override
    public Response put(String endpointId, byte[] body) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, body, HttpMethod.PUT)));
    }

    @Override
    public Response post(String endpointId, byte[] body) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, body, HttpMethod.POST)));
    }

    @Override
    public Response delete(String endpointId) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, null, HttpMethod.DELETE)));
    }

    @Override
    public Response patch(String endpointId, byte[] body) {
        return unwrapper.apply(forwarder.apply(wrap(endpointId, body, HttpMethod.PATCH)));
    }

}
