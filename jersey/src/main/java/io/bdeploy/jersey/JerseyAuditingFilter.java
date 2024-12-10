package io.bdeploy.jersey;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.AuditRecord.Severity;
import io.bdeploy.common.audit.Auditor;
import jakarta.inject.Inject;
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
    private Auditor auditor;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        StatusType status = responseContext.getStatusInfo();

        // only audit *failed* requests.
        if (status.getFamily() == Family.SUCCESSFUL) {
            return;
        }

        auditor.audit(AuditRecord.Builder.fromRequest(requestContext).setSeverity(Severity.WARNING)
                .setMessage("HTTP: " + status.getStatusCode() + ": " + status.getReasonPhrase()).build());
    }

}
