package io.bdeploy.bhive.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.SkipSubTreeVisitor;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.util.PathHelper;

/**
 * Tests a few of the basic tools.
 */
@ExtendWith(TestActivityReporter.class)
class BasicToolTest {

    @RegisterExtension
    final TestCliTool tools = new TestCliTool(new BHiveCli());

    @SlowTest
    @Test
    void testRoundTrip(@TempDir Path tmp, ActivityReporter reporter) throws Exception {
        Path hiveDir = tmp.resolve("hive");
        Path srcDir = tmp.resolve("src");
        Path expDir = tmp.resolve("exp");
        Path smallSrcDir = ContentHelper.genSimpleTestTree(tmp, "smallSrc");
        Path smallSrc2Dir = ContentHelper.genSimpleTestTree(tmp, "smallSrc2");

        String hiveArg = "--hive=" + hiveDir.toString();
        ContentHelper.genTestTree(srcDir, 1, 1, 1, 2, 0, 0);

        // import a test directory
        tools.execute(InitTool.class, hiveArg);
        tools.execute(ImportTool.class, hiveArg, "--source=" + srcDir, "--manifest=test:v1", "--label=x:y");

        // check programmatically whether this looks ok.
        try (BHive h = new BHive(hiveDir.toUri(), null, reporter)) {
            Set<Manifest.Key> manifests = h.execute(new ManifestListOperation());
            assertThat(manifests.size(), is(1));
            assertTrue(manifests.contains(Manifest.Key.parse("test:v1")));

            Manifest m = h.execute(new ManifestLoadOperation().setManifest(new Manifest.Key("test", "v1")));
            Map<String, String> labels = m.getLabels();
            assertThat(labels.size(), is(1));
            assertThat(labels.get("x"), is("y"));
        }

        // check with tool whether the manifest is there
        var output = tools.execute(ManifestTool.class, hiveArg, "--list", "--manifest=test:v1");
        assertThat(output.get(0).get("Key"), containsString("test:v1"));

        // perform FSCK to check for broken database
        output = tools.execute(FsckTool.class, hiveArg, "--manifest=test:v1");
        assertThat(output.get(0).get("message"), is(equalTo("Success")));

        // export to other directory and compare with original source.
        tools.execute(ExportTool.class, hiveArg, "--manifest=test:v1", "--target=" + expDir.toString());

        ContentHelper.checkDirsEqual(srcDir, expDir);

        Manifest.Key anotherKey = new Manifest.Key("another", "v1");
        tools.execute(ImportTool.class, hiveArg, "--source=" + smallSrcDir, "--manifest=" + anotherKey);

        Manifest.Key anotherKey2 = new Manifest.Key("another", "v2");
        PathHelper.deleteIfExistsRetry(smallSrc2Dir.resolve("test.txt"));
        Files.write(smallSrc2Dir.resolve("another.txt"), Arrays.asList("Test Content"));
        tools.execute(ImportTool.class, hiveArg, "--source=" + smallSrc2Dir, "--manifest=" + anotherKey2);

        var raw = tools.execute(TreeTool.class, hiveArg, "--list=" + anotherKey).getRawOutput();
        assertEquals(9, raw.length);

        raw = tools.execute(TreeTool.class, hiveArg, "--diff=" + anotherKey, "--diff=" + anotherKey2).getRawOutput();
        // one content diff (root), one only left (test.txt), one only right (another.txt).
        assertEquals(7, raw.length);

        tools.execute(ManifestTool.class, hiveArg, "--delete", "--manifest=test:v1");
        tools.execute(ManifestTool.class, hiveArg, "--delete", "--manifest=another:v2");

        // check programmatically whether this looks ok.
        try (BHive h = new BHive(hiveDir.toUri(), null, reporter)) {
            Set<Manifest.Key> manifests = h.execute(new ManifestListOperation());
            assertThat(manifests.size(), is(1));
            assertTrue(manifests.contains(Manifest.Key.parse("another:v1")));

            Manifest m = h.execute(new ManifestLoadOperation().setManifest(anotherKey));
            Map<String, String> labels = m.getLabels();
            assertThat(labels.size(), is(0));
        }

        tools.execute(PruneTool.class, hiveArg);

        // remaining objects of the simple manifest. 3 files, 3 trees, 1 manifest
        // plus remaining hive management objects: 1 database lock, 1 manifest lock
        SkipSubTreeVisitor visitor = new SkipSubTreeVisitor(hiveDir.resolve("logs"));
        Files.walkFileTree(hiveDir, visitor);
        assertThat(visitor.getFileCount(), is(9L));
    }

    @Test
    void helpTest(@TempDir Path tmp) {
        ToolBase.setTestMode(true);

        assertThrows(IllegalArgumentException.class, () -> {
            BHiveCli.main(); // must show help
        });

        Path tmpout = tmp.resolve("tmpout.txt");
        Path tmppout = tmp.resolve("tmppout.txt");

        assertThrows(IllegalArgumentException.class, () -> {
            // must show help, missing --hive argument on 'manifest' tool.
            BHiveCli.main("-q", "-v", "-o", tmpout.toString(), "-op", tmppout.toString(), "manifest", "--list");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            BHiveCli.main("token");
        });
    }
}
