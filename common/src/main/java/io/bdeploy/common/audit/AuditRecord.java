package io.bdeploy.common.audit;

import java.security.Principal;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.common.cfg.Configuration;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

/**
 * Immutable object representing an audit entry.
 */
public class AuditRecord {

    public enum Severity {
        @JsonEnumDefaultValue
        NORMAL,
        WARNING,
        ERROR
    }

    public final String who;
    public final String what;
    public final String parameters;
    public final String method;
    public final String message;
    public final Severity severity;

    private AuditRecord(Builder builder) {
        if (builder.parameters.isEmpty()) {
            parameters = "No parameters";
        } else {
            parameters = builder.parameters.toString();
        }
        what = builder.what;
        method = builder.method;
        message = builder.message;
        severity = builder.severity;
        if (builder.who == null) {
            who = "<UNKNOWN>"; // raise severity to error if the user is not known.
        } else {
            who = builder.who;
        }
    }

    @Override
    public String toString() {
        return "Audit record [what=" + what + " method=" + method + " severity=" + severity + " who=" + who + " parameters="
                + parameters + "]";
    }

    public static class Builder {

        private final Map<String, String> parameters = new TreeMap<>();
        private String who;
        private String what;
        private String method = "-";
        private String message = "Execute";
        private Severity severity = Severity.NORMAL;

        public static Builder fromRequest(ContainerRequestContext context) {
            Builder builder = new Builder();
            UriInfo uriInfo = context.getUriInfo();

            builder.setWho(context.getSecurityContext());
            builder.setWhat(uriInfo.getPath(true));
            builder.setMethod(context.getMethod());

            uriInfo.getQueryParameters(true).forEach((k, v) -> builder.addParameter("q:" + k, v.toString()));
            uriInfo.getPathParameters(true).forEach((k, v) -> builder.addParameter("p:" + k, v.toString()));

            return builder;
        }

        public static Builder fromSystem() {
            Builder builder = new Builder();

            builder.setWho(System.getProperty("user.name"));

            return builder;
        }

        public Builder setWho(SecurityContext context) {
            if (context != null) {
                Principal p = context.getUserPrincipal();
                if (p != null) {
                    this.who = p.getName();
                }
            }
            return this;
        }

        public Builder setWho(String who) {
            this.who = who;
            return this;
        }

        public Builder setWhat(String what) {
            this.what = what;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder addParameter(String key, String value) {
            parameters.put(key, value);
            return this;
        }

        public Builder addParameters(Configuration cfg) {
            cfg.getAllRawObjects().forEach((k, v) -> addParameter(k, v.toString()));
            return this;
        }

        public Builder addParameters(Map<String, String> params) {
            parameters.putAll(params);
            return this;
        }

        public Builder setSeverity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder setMethod(String method) {
            this.method = method;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(this);
        }

    }

}