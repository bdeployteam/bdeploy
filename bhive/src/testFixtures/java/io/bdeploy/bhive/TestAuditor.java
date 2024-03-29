package io.bdeploy.bhive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.Auditor;

public class TestAuditor implements Auditor {

    public List<AuditRecord> audits = new CopyOnWriteArrayList<>();

    @Override
    public void audit(AuditRecord rec) {
        this.audits.add(rec);
    }

    @Override
    public void close() {
    }

}
