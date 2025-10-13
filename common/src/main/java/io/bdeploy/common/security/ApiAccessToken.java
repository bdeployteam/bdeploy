package io.bdeploy.common.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Represents a secure access token. Whoever has a correctly signed token of
 * this type has access to the remote API.
 * <p>
 * A token may be weak, in which case it allows access only to endpoints which
 * allow weak tokens explicitly.
 * <p>
 * This mechanism is used to provide tokens which are allowed solely for automated
 * applications like the client launcher, so it can fetch updates and applications.
 */
public class ApiAccessToken {

    public static final String SYSTEM_USER = "BDeploy System";

    String it; // issuedTo

    private final List<ScopedPermission> c = new ArrayList<>(); // permissions
    private long ia; // issuedAt
    private long vu; // validUntil
    private boolean wt; // weakToken

    public String getIssuedTo() {
        return it;
    }

    public boolean isValid() {
        return vu > System.currentTimeMillis();
    }

    public boolean isWeak() {
        return wt;
    }

    public boolean isSystem() {
        return SYSTEM_USER.equals(it);
    }

    public Collection<ScopedPermission> getPermissions() {
        return Collections.unmodifiableCollection(c);
    }

    public static final class Builder {

        private final ApiAccessToken token = new ApiAccessToken();

        public Builder() {
            token.ia = System.currentTimeMillis();
            token.vu = token.ia + TimeUnit.DAYS.toMillis(17800);
        }

        public Builder setIssuedTo(String name) {
            token.it = name;
            return this;
        }

        public Builder forSystem() {
            token.it = SYSTEM_USER;
            return this;
        }

        public Builder setWeak(boolean weak) {
            token.wt = weak;
            return this;
        }

        public Builder addPermission(ScopedPermission permission) {
            if (!permission.isGlobal()) {
                throw new IllegalArgumentException("Only global permissions are allowed in access tokens");
            }
            token.c.add(permission);
            return this;
        }

        public Builder addPermission(Collection<ScopedPermission> permissions) {
            permissions.forEach(this::addPermission);
            return this;
        }

        public ApiAccessToken build() {
            return token;
        }

    }
}
