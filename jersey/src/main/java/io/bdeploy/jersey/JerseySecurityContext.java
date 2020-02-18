package io.bdeploy.jersey;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;

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

    @Override
    public Principal getUserPrincipal() {
        if (onBehalfOf != null) {
            return () -> "[" + onBehalfOf + "]";
        }

        return token::getIssuedTo;
    }

    @Override
    public boolean isUserInRole(String role) {
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

    /**
     * Returns a boolean indicating whether the security token grants the requested permission. Please note that
     * the token only contains the GLOBAL permissions. When {@code false} is returned then the LOCAL permissions must also
     * be evaluated before denying access to a given resource.
     *
     * @param scopedPermission the required permission
     * @return {@code true} if authorized or {@code false} otherwise
     */
    public boolean isAuthorized(ScopedPermission scopedPermission) {
        for (ScopedPermission sc : token.getPermissions()) {
            if (sc.satisfies(scopedPermission)) {
                return true;
            }
        }
        return false;
    }

}
