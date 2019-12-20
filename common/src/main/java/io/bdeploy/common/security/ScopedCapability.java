package io.bdeploy.common.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ScopedCapability implements Comparable<ScopedCapability> {

    public enum Capability {
        READ,
        WRITE,
        ADMIN
    }

    public final String scope;
    public final ScopedCapability.Capability capability;

    @JsonCreator
    public ScopedCapability(@JsonProperty("scope") String scope, @JsonProperty("capability") ScopedCapability.Capability cap) {
        this.scope = scope;
        this.capability = cap;
    }

    @Override
    public int compareTo(ScopedCapability o) {
        if (scope == null) {
            if (o.scope != null) {
                return -1; // a global scoped capability is always ranked first
            }
        } else {
            int x = scope.compareTo(o.scope);
            if (x != 0) {
                return x;
            }
        }
        return capability.compareTo(o.capability);
    }
}