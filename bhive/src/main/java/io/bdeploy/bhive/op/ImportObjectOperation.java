package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.NoAudit;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Import a single blob from existing data in memory into the {@link ObjectDatabase}. Useful mainly
 * when building artificial {@link Tree}.
 */
public class ImportObjectOperation extends BHive.TransactedOperation<ObjectId> {

    @NoAudit
    private byte[] data;

    @Override
    public ObjectId callTransacted() throws Exception {
        try (Activity activity = getActivityReporter().start("Importing data...", -1)) {
            assertNotNull(data, "Data to import not set");
            return getObjectManager().db(x -> x.addObject(data));
        }
    }

    /**
     * Set the {@link Path} to import from. Must be an existing file.
     */
    public ImportObjectOperation setData(byte[] data) {
        this.data = data.clone();
        return this;
    }

}
