package io.bdeploy.jersey;

import java.io.IOException;
import java.util.function.Supplier;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;

@Provider
public class JerseySseActivityProxyClientFilter implements ClientRequestFilter {

    public static final String PROXY_SCOPE_HEADER = "X-Proxy-Activity-Scope";

    private final Supplier<String> proxyScope;

    public JerseySseActivityProxyClientFilter(Supplier<String> proxyScope) {
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
