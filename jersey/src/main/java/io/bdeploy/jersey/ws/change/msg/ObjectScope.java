package io.bdeploy.jersey.ws.change.msg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates scoping logic.
 */
public class ObjectScope implements Comparable<ObjectScope> {

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
            if (!Objects.equals(scope.get(i), other.scope.get(i))) {
                return false;
            }
        }

        return true;
    }

    public int score(ObjectScope other) {
        if (!matches(other)) {
            return 0;
        }

        // might be empty scope, needs special handling.
        if (scope.isEmpty() && other.scope.isEmpty()) {
            return 100;
        }

        // the scope matches. the score is the percentage of present scope parts, so compare length.
        // if our scope is longer than the compare scope we limit the match to 100 - full match.
        return Math.min(100, Math.round((100f * other.scope.size()) / scope.size()));
    }

    public int length() {
        return scope.size();
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

    @Override
    public int compareTo(ObjectScope o) {
        if (scope.size() > o.scope.size()) {
            return 1;
        }
        if (scope.size() > o.scope.size()) {
            return -1;
        }
        for (int i = 0; i < scope.size(); ++i) {
            int r = scope.get(i).compareTo(o.scope.get(i));
            if (r != 0) {
                return r;
            }
        }
        return 0;
    }

}
