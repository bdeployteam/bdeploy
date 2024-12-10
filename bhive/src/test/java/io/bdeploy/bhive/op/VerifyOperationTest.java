package io.bdeploy.bhive.op;

import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MISSING;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MODIFIED;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.UNMODIFIED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.VerifyOperation.VerifiedBlobView;
import io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus;
import io.bdeploy.common.ContentHelper;

@ExtendWith(TestHive.class)
public class VerifyOperationTest {

    public static final String TEST1 = "test file content 1";
    public static final String TEST2 = "test file content 2";
    public static final String TEST3 = "$(&*!/@äö?§x 🤣 \\n $(&*!\\";

    @Test
    void testSame(BHive hive, @TempDir Path tmp) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "src");
        Manifest.Key key = new Manifest.Key("test", "v1");

        try (Transaction t = hive.getTransactions().begin()) {
            assertThat(hive.execute(new ImportOperation().setManifest(key).setSourcePath(src)), is(key));
            var result = hive.execute(new VerifyOperation().setManifest(key).setTargetPath(src));
            assertThat(getByStatus(result, MISSING).size(), is(0));
            assertThat(getByStatus(result, MODIFIED).size(), is(0));
            assertThat(getByStatus(result, UNMODIFIED).size(), is(3));

            Files.delete(src.resolve(Paths.get("dir", "file.txt")));
            Files.writeString(src.resolve("test.txt"), "abc");
            result = hive.execute(new VerifyOperation().setManifest(key).setTargetPath(src));
            assertThat(getByStatus(result, MISSING).size(), is(1));
            assertThat(getByStatus(result, MODIFIED).size(), is(1));
            assertThat(getByStatus(result, UNMODIFIED).size(), is(1));
        }
    }

    private static List<VerifiedBlobView> getByStatus(List<VerifiedBlobView> list, VerifyOpStatus status) {
        return list.stream().filter(view -> view.status == status).collect(Collectors.toList());
    }
}
