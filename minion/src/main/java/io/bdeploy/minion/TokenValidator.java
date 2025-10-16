package io.bdeploy.minion;

import java.util.Collection;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.user.UserDatabase;

public class TokenValidator implements Predicate<ApiAccessToken> {

    private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);

    private static Predicate<Collection<ScopedPermission>> isGlobalAdmin = perms -> perms.stream()
            .anyMatch(p -> ScopedPermission.GLOBAL_ADMIN.equals(p));

    private final UserDatabase userDatabase;

    public TokenValidator(UserDatabase userDatabase) {
        this.userDatabase = userDatabase;
    }

    @Override
    public boolean test(ApiAccessToken token) {
        if (token.isSystem()) {
            return true;
        }

        String user = token.getIssuedTo();
        UserInfo userInfo = userDatabase.getUser(user);

        /*
         * TODO This code allows tokens with invalid users as servers that have been initialized LONG ago will have those as
         * master token. Changing this will invalidate these tokens (which are still around). Nevertheless this poses a
         * security threat, so we should update the code and provide a migration.
         */
        if (userInfo == null) {
            if (user.startsWith("[") && user.endsWith("]")) {
                // on behalf of remote user (e.g. from central).
                return true;
            }
            log.error("User not available: {}. Allowing to support legacy tokens.", user);
            return true;
        }

        // Validate that the user is still active and still has the same permissions as the token
        boolean tokenHasGlobalAdminPermissions = isGlobalAdmin.test(token.getPermissions());
        boolean userHasGlobalAdminPermissions = !userInfo.inactive && isGlobalAdmin.test(userInfo.permissions);
        if (tokenHasGlobalAdminPermissions && !userHasGlobalAdminPermissions) {
            return false;
        }

        return userDatabase.isAuthenticationValid(userInfo);
    }
}
