package io.bdeploy.jersey;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedCapability;

/**
 * A simple {@link SecurityContext} which provides information based on the
 * authentication token used to authenticate a service call.
 */
public class JerseySecurityContext implements SecurityContext {

    private final ApiAccessToken token;
    private final String onBehalfOf;

    public JerseySecurityContext(ApiAccessToken token, String onBehalfOf) {
        this.token = token;
        this.onBehalfOf = onBehalfOf;
    }

    public boolean hasCapability(String scope, ScopedCapability.Capability cap) {
        return token.hasCapability(scope, cap);
    }

    @Override
    public Principal getUserPrincipal() {
        if (onBehalfOf != null) {
            return () -> "[" + onBehalfOf + "]";
        }

        return token::getIssuedTo;
    }

    @Override
    public boolean isUserInRole(String role) {
        // this is not used. it is required for @RolesAllowed, which is too static for DCS.
        return false;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public String getAuthenticationScheme() {
        return JerseyAuthenticationProvider.AUTHENTICATION_SCHEME;
    }

}
