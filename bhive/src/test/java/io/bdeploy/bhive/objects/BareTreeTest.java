package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class BareTreeTest {

    @Test
    void impExp(@TempDir Path tmp, ActivityReporter reporter) throws IOException {
        Path hiveDir = tmp.resolve("hive");
        Path mySource = ContentHelper.genSimpleTestTree(tmp, "source");
        Path myTarget = tmp.resolve("target");

        try (BHive hive = new BHive(hiveDir.toUri(), reporter); Transaction t = hive.getTransactions().begin()) {
            ObjectId oid = hive.execute(new ImportTreeOperation().setSourcePath(mySource));
            hive.execute(new ExportTreeOperation().setSourceTree(oid).setTargetPath(myTarget));
        }

        ContentHelper.checkDirsEqual(mySource, myTarget);
    }

}
