package io.bdeploy.jersey.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A {@linkplain Auditor auditor} implementation that is using a logger to write the audit records.
 */
public class Log4jAuditor implements Auditor {

    private static final Logger log = LoggerFactory.getLogger(Log4jAuditor.class);

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public void audit(AuditRecord record) {
        MDC.put("WHO", record.who);
        MDC.put("WHAT", record.what);
        MDC.put("PARAMETERS", record.parameters);
        MDC.put("METHOD", record.method);

        switch (record.severity) {
            case NORMAL:
                log.info(record.message);
                break;
            case WARNING:
                log.warn(record.message);
                break;
            case ERROR:
                log.error(record.message);
                break;
        }
    }

}
