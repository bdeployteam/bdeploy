package io.bdeploy.jersey;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.SecurityContext;

public class JerseyOnBehalfOfFilter implements ClientRequestFilter {

    public static final String ON_BEHALF_OF_HEADER = "X-On-Behalf-Of";

    private final String onBehalf;

    public JerseyOnBehalfOfFilter(SecurityContext context) {
        this.onBehalf = (context == null || context.getUserPrincipal() == null) ? null : context.getUserPrincipal().getName();
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (onBehalf != null) {
            requestContext.getHeaders().add(ON_BEHALF_OF_HEADER, onBehalf);
        }
    }

}
