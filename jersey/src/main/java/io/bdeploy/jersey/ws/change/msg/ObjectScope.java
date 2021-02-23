package io.bdeploy.jersey.ws.change.msg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates scoping logic.
 */
public class ObjectScope {

    /**
     * An empty scope matching any incoming scope.
     */
    public static final ObjectScope EMPTY = new ObjectScope(Collections.emptyList());

    private final List<String> scope;

    /**
     * Creates an {@link ObjectScope} from the {@link List} representation of a scope.
     */
    @JsonCreator
    public ObjectScope(@JsonProperty("scope") List<String> scope) {
        this.scope = scope == null ? Collections.emptyList() : scope;
    }

    /**
     * Create an {@link ObjectScope} from one or more scope parts.
     */
    public ObjectScope(String... scope) {
        this.scope = Arrays.asList(scope);
    }

    /**
     * Returns true if this {@link ObjectScope} is a sub-scope (or exact match) of the given {@link ObjectScope}.
     */
    public boolean matches(ObjectScope other) {
        // not interested if our scope is more detailed than the given one
        if (scope.size() > other.scope.size()) {
            return false;
        }

        // compare all scope parts. all scope parts we have must be present on the other scope.
        // the other scope is allowed to be more detailed.
        for (int i = 0; i < scope.size(); ++i) {
            if (!scope.get(i).equals(other.scope.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return scope.toString();
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ObjectScope other = (ObjectScope) obj;
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        return true;
    }

}
