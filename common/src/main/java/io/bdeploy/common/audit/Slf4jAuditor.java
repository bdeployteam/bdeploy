package io.bdeploy.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A {@linkplain Auditor auditor} implementation that is using a logger to write the audit records.
 */
public class Slf4jAuditor implements Auditor {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAuditor.class);

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public void audit(AuditRecord rec) {
        MDC.put("WHO", rec.who);
        MDC.put("WHAT", rec.what);
        MDC.put("PARAMETERS", rec.parameters);
        MDC.put("METHOD", rec.method);

        switch (rec.severity) {
            case NORMAL:
                log.info(rec.message);
                break;
            case WARNING:
                log.warn(rec.message);
                break;
            case ERROR:
                log.error(rec.message);
                break;
        }
    }

}
