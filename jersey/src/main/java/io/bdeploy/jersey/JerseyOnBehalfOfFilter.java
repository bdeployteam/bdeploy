package io.bdeploy.jersey;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.SecurityContext;

public class JerseyOnBehalfOfFilter implements ClientRequestFilter {

    public static final String ON_BEHALF_OF_HEADER = "X-On-Behalf-Of";

    private final String onBehalf;

    public JerseyOnBehalfOfFilter(SecurityContext context) {
        this.onBehalf = context == null ? null : context.getUserPrincipal().getName();
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (onBehalf != null) {
            requestContext.getHeaders().add(ON_BEHALF_OF_HEADER, onBehalf);
        }
    }

}
