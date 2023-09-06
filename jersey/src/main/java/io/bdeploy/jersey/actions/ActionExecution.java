package io.bdeploy.jersey.actions;

import java.util.Comparator;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.UuidHelper;
import jakarta.annotation.Generated;
import jakarta.ws.rs.core.SecurityContext;

public class ActionExecution implements Comparable<ActionExecution> {

    private static final Comparator<ActionExecution> COMPARATOR = Comparator.comparing(ActionExecution::getId)
            .thenComparing(ActionExecution::getStart);

    private final String id;
    private final String name;
    private String source;
    private final long start;

    public ActionExecution(String name) {
        this.id = UuidHelper.randomId();
        this.name = name;
        this.start = System.currentTimeMillis();
    }

    @JsonCreator
    public ActionExecution(@JsonProperty("id") String id, @JsonProperty("name") String name, @JsonProperty("start") long start) {
        this.id = id;
        this.name = name;
        this.start = start;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getStart() {
        return start;
    }

    public static ActionExecution from(SecurityContext context) {
        String principal = context == null ? "Unknown" : context.getUserPrincipal().getName();
        return new ActionExecution(principal);
    }

    public static ActionExecution fromSystem() {
        return new ActionExecution(ApiAccessToken.SYSTEM_USER);
    }

    @Override
    public String toString() {
        return "Execution [id=" + id + ", by=" + name + (source != null ? (", source=" + source) : "") + ", started="
                + (System.currentTimeMillis() - start) + "ms ago]";
    }

    @Override
    public int compareTo(ActionExecution o) {
        return COMPARATOR.compare(this, o);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        return Objects.hash(id, start);
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
        ActionExecution other = (ActionExecution) obj;
        return Objects.equals(id, other.id) && start == other.start;
    }

}
