package io.bdeploy.bhive.misc;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestAuditor;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.AuditRecord.Severity;

@ExtendWith(TestHive.class)
class HiveAuditTest {

    @Test
    void testAudit(BHive hive, @TempDir Path tmp) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "src");
        Manifest.Key key = new Manifest.Key("test", "v1");

        try (Transaction t = hive.getTransactions().begin()) {
            assertThat(hive.execute(new ImportOperation().setManifest(key).setSourcePath(src)), is(key));
        }

        Path fileToMessWith = hive.execute(new BHive.Operation<Path>() {

            @Override
            public Path call() throws Exception {
                return getObjectManager().db(x -> {
                    return x.getObjectFile(ObjectId.parse(ContentHelper.TEST_TXT_OID));
                });
            }
        });

        Files.write(fileToMessWith, Collections.singleton("This is something broken"));

        assertThrows(IllegalStateException.class, () -> {
            hive.execute(new ExportOperation().setManifest(key).setTarget(tmp.resolve("export")));
        });

        TestAuditor auditor = (TestAuditor) hive.getAuditor();
        List<AuditRecord> lines = auditor.audits;
        assertEquals(6, lines.size());
        assertEquals(Severity.ERROR, lines.get(5).severity);
        assertEquals("Asynchronous operation(s) failed", lines.get(5).message);
    }

}
