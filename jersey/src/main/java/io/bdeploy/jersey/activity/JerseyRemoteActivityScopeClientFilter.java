package io.bdeploy.jersey.activity;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JerseyRemoteActivityScopeClientFilter implements ClientRequestFilter {

    public static final String PROXY_SCOPE_HEADER = "X-Proxy-Activity-Scope";

    private final Supplier<String> proxyScope;

    public JerseyRemoteActivityScopeClientFilter(Supplier<String> proxyScope) {
        this.proxyScope = proxyScope;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String scope = proxyScope.get();
        if (scope != null) {
            requestContext.getHeaders().add(PROXY_SCOPE_HEADER, scope);
        }
    }

}
