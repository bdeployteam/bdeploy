package io.bdeploy.jersey;

import java.io.IOException;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.AuditRecord.Severity;
import io.bdeploy.common.audit.Auditor;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;

/**
 * A {@link ContainerResponseFilter} which audit's requests and their results.
 * <p>
 * IMPORTANT: The auditing happens on <b>return</b> of a request. Pay attention when reading log files.
 */
@jakarta.ws.rs.ext.Provider
public class JerseyAuditingFilter implements ContainerResponseFilter {

    @Inject
    Auditor auditor;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (JerseyCorsFilter.isPreflightRequest(requestContext)) {
            return;
        }

        StatusType status = responseContext.getStatusInfo();

        // skip GET reqeusts which are successful
        if (status.getFamily() == Family.SUCCESSFUL && requestContext.getMethod().equals(HttpMethod.GET)) {
            return;
        }

        auditor.audit(AuditRecord.Builder.fromRequest(requestContext)
                .setSeverity(responseContext.getStatus() > 400 ? Severity.WARNING : Severity.NORMAL)
                .setMessage(status.getStatusCode() + ": " + status.getReasonPhrase()).build());
    }

}
