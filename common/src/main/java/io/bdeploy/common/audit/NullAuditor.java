package io.bdeploy.common.audit;

public class NullAuditor implements Auditor {

    @Override
    public void audit(AuditRecord rec) {
        // do nothing.
    }

    @Override
    public void close() {
        // do nothing
    }

}