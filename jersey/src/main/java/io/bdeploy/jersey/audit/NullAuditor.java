package io.bdeploy.jersey.audit;

public class NullAuditor implements Auditor {

    @Override
    public void audit(AuditRecord record) {
        // do nothing.
    }

    @Override
    public void close() {
        // do nothing
    }

}