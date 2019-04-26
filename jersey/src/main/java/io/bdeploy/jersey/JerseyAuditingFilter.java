package io.bdeploy.jersey;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response.StatusType;

import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.AuditRecord.Severity;

/**
 * A {@link ContainerResponseFilter} which audit's requests and their results.
 * <p>
 * IMPORTANT: The auditing happens on <b>return</b> of a request. Pay attention when reading log files.
 */
@javax.ws.rs.ext.Provider
public class JerseyAuditingFilter implements ContainerResponseFilter {

    @Inject
    Auditor auditor;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (JerseyCorsFilter.isPreflightRequest(requestContext)) {
            return;
        }

        StatusType status = responseContext.getStatusInfo();
        auditor.audit(AuditRecord.Builder.fromRequest(requestContext)
                .setSeverity(responseContext.getStatus() > 400 ? Severity.WARNING : Severity.NORMAL)
                .setMessage(status.getStatusCode() + ": " + status.getReasonPhrase()).build());
    }

}
