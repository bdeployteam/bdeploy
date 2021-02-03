package io.bdeploy.bhive.op;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class OperationTests {

    @Test
    public void testBasicOperations(BHive hive, @TempDir Path tmp) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "src");
        Path tmpDir = tmp.resolve("exp");

        assertThat(hive.execute(new ManifestListOperation()).size(), is(0));

        Manifest.Key key = new Manifest.Key("test", "v1");

        try (Transaction t = hive.getTransactions().begin()) {
            assertThat(hive.execute(new ImportOperation().setManifest(key).setSourcePath(src)), is(key));
        }

        Set<Manifest.Key> list = hive.execute(new ManifestListOperation());
        assertThat(list.size(), is(1));
        assertTrue(list.contains(key));

        assertThat(hive.execute(new ExportOperation().setManifest(key).setTarget(tmpDir.resolve("test"))), is(key));

        assertThat(hive.execute(new ManifestDeleteOperation().setToDelete(key)), is(key));
        assertThat(hive.execute(new ManifestListOperation()).size(), is(0));
    }

}
