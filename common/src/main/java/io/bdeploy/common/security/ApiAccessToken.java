package io.bdeploy.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    String it; // issuedTo
    List<ScopedCapability> c = new ArrayList<>(); // capabilities
    long ia; // issuedAt
    long vu; // validUntil
    boolean wt; // weakToken
    public static final ScopedCapability ADMIN_CAPABILITY = new ScopedCapability(null, Capability.ADMIN);

    public String getIssuedTo() {
        return it;
    }

    public boolean isGlobalAdmin() {
        return hasCapability(null, Capability.ADMIN);
    }

    public boolean hasCapability(String scope, Capability required) {
        for (ScopedCapability cap : c) {
            // null and empty string are equal for this calculation...
            if (Objects.equals((cap.scope == null ? "" : cap.scope), (scope == null ? "" : scope))
                    && cap.capability == required) {
                return true;
            }
        }
        return false;
    }

    public boolean isValid() {
        return vu > System.currentTimeMillis();
    }

    public boolean isWeak() {
        return wt;
    }

    public enum Capability {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        ADMIN
    }

    public static final class ScopedCapability {

        public final String scope;
        public final Capability capability;

        @JsonCreator
        public ScopedCapability(@JsonProperty("scope") String scope, @JsonProperty("capability") Capability cap) {
            this.scope = scope;
            this.capability = cap;
        }
    }

    public static final class Builder {

        ApiAccessToken token = new ApiAccessToken();

        public Builder() {
            token.ia = System.currentTimeMillis();
            token.vu = token.ia + TimeUnit.DAYS.toMillis(17800);
        }

        public Builder setIssuedTo(String name) {
            token.it = name;
            return this;
        }

        public Builder setValidUntil(long timestamp) {
            token.vu = timestamp;
            return this;
        }

        public Builder setWeak(boolean weak) {
            token.wt = weak;
            return this;
        }

        public Builder addCapability(ScopedCapability cap) {
            token.c.add(cap);
            return this;
        }

        public ApiAccessToken build() {
            return token;
        }

    }
}