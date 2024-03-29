package io.bdeploy.common.audit;

/**
 * A {@link Auditor} is responsible for persisting {@link AuditRecord}s.
 */
public interface Auditor extends AutoCloseable {

    /**
     * Stores the given audit record.
     *
     * @param rec
     *            record to write
     */
    public void audit(AuditRecord rec);

    @Override
    public void close();

}
