package io.bdeploy.jersey;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import jakarta.ws.rs.core.SecurityContext;

/**
 * A simple {@link SecurityContext} which provides information based on the
 * authentication token used to authenticate a service call.
 */
public class JerseySecurityContext implements SecurityContext {

    private static final ScopedPermission WEAK_READ = new ScopedPermission(Permission.READ);

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

    public Collection<ScopedPermission> getPermissions() {
        return token.getPermissions();
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
        Collection<ScopedPermission> perms = token.getPermissions();

        // if the token is weak, it has implicit global read, as it may only access @WeakTokenAllowed annotated
        // endpoints anyway. This is an orthogonal mechanism to "normal" authentication.
        if (token.isWeak()) {
            perms = Collections.singleton(WEAK_READ);
        }

        for (ScopedPermission sc : perms) {
            if (sc.satisfies(scopedPermission)) {
                return true;
            }
        }
        return false;
    }

}
