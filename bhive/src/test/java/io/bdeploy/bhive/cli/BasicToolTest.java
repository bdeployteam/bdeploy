package io.bdeploy.bhive.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Splitter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.SkipSubTreeVisitor;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.cli.ToolBase;

/**
 * Tests a few of the basic tools.
 */
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class BasicToolTest {

    @RegisterExtension
    final TestCliTool tools = new TestCliTool(new BHiveCli());

    @SlowTest
    @Test
    public void testRoundTrip(@TempDir Path tmp, ActivityReporter reporter) throws Exception {
        Path hiveDir = tmp.resolve("hive");
        Path srcDir = tmp.resolve("src");
        Path expDir = tmp.resolve("exp");
        Path smallSrcDir = ContentHelper.genSimpleTestTree(tmp, "smallSrc");
        Path smallSrc2Dir = ContentHelper.genSimpleTestTree(tmp, "smallSrc2");

        String hiveArg = "--hive=" + hiveDir.toString();
        ContentHelper.genTestTree(srcDir, 1, 1, 1, 2, 0, 0);

        // import a test directory
        tools.getTool(ImportTool.class, hiveArg, "--source=" + srcDir, "--manifest=test:v1", "--label=x:y").run();

        // check programmatically whether this looks ok.
        try (BHive h = new BHive(hiveDir.toUri(), reporter)) {
            SortedSet<Manifest.Key> manifests = h.execute(new ManifestListOperation());
            assertThat(manifests.size(), is(1));
            assertTrue(manifests.contains(Manifest.Key.parse("test:v1")));

            Manifest m = h.execute(new ManifestLoadOperation().setManifest(new Manifest.Key("test", "v1")));
            Map<String, String> labels = m.getLabels();
            assertThat(labels.size(), is(1));
            assertThat(labels.get("x"), is("y"));
        }

        // check with tool whether the manifest is there
        ManifestTool list = tools.getTool(ManifestTool.class, hiveArg, "--list", "--manifest=test:v1");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (PrintStream ps = new PrintStream(baos)) {
                list.setOutput(ps);
                list.run();
            }
            String output = baos.toString(StandardCharsets.UTF_8.name());
            assertThat(output, startsWith("test:v1"));
        }

        // perform FSCK to check for broken database
        FsckTool check = tools.getTool(FsckTool.class, hiveArg, "--manifest=test:v1");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (PrintStream ps = new PrintStream(baos)) {
                check.setOutput(ps);
                check.run();
            }
            String output = baos.toString(StandardCharsets.UTF_8.name());
            assertThat(output.trim(), is("Check OK"));
        }

        // export to other directory and compare with original source.
        tools.getTool(ExportTool.class, hiveArg, "--manifest=test:v1", "--target=" + expDir.toString()).run();

        ContentHelper.checkDirsEqual(srcDir, expDir);

        Manifest.Key anotherKey = new Manifest.Key("another", "v1");
        tools.getTool(ImportTool.class, hiveArg, "--source=" + smallSrcDir, "--manifest=" + anotherKey).run();

        Manifest.Key anotherKey2 = new Manifest.Key("another", "v2");
        Files.delete(smallSrc2Dir.resolve("test.txt"));
        Files.write(smallSrc2Dir.resolve("another.txt"), Arrays.asList("Test Content"));
        tools.getTool(ImportTool.class, hiveArg, "--source=" + smallSrc2Dir, "--manifest=" + anotherKey2).run();

        TreeTool treetool = tools.getTool(TreeTool.class, hiveArg, "--list=" + anotherKey);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            treetool.setOutput(new PrintStream(baos));
            treetool.run();

            List<String> out = Splitter.on(System.getProperty("line.separator")).splitToList(baos.toString());
            // 4 elements plus an empty final line
            assertEquals(5, out.size());
        }

        TreeTool difftool = tools.getTool(TreeTool.class, hiveArg, "--diff=" + anotherKey, "--diff=" + anotherKey2);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            difftool.setOutput(new PrintStream(baos));
            difftool.run();

            List<String> out = Splitter.on(System.getProperty("line.separator")).splitToList(baos.toString());
            // one content diff (root), one only left (test.txt), one only right
            // (another.txt), one final empty line.
            assertEquals(5, out.size());
        }

        tools.getTool(ManifestTool.class, hiveArg, "--delete", "--manifest=test:v1").run();
        tools.getTool(ManifestTool.class, hiveArg, "--delete", "--manifest=another:v2").run();

        // check programmatically whether this looks ok.
        try (BHive h = new BHive(hiveDir.toUri(), reporter)) {
            SortedSet<Manifest.Key> manifests = h.execute(new ManifestListOperation());
            assertThat(manifests.size(), is(1));
            assertTrue(manifests.contains(Manifest.Key.parse("another:v1")));

            Manifest m = h.execute(new ManifestLoadOperation().setManifest(anotherKey));
            Map<String, String> labels = m.getLabels();
            assertThat(labels.size(), is(0));
        }

        tools.getTool(PruneTool.class, hiveArg).run();

        // remaining objects of the simple manifest. 2 files, 2 trees, 1 manifest
        // plus remaining hive management objects. 1 .dblock, 1 manifest.db, audit.log
        SkipSubTreeVisitor visitor = new SkipSubTreeVisitor(hiveDir.resolve("logs"));
        Files.walkFileTree(hiveDir, visitor);
        assertThat(visitor.getFileCount(), is(7L));
    }

    @Test
    public void helpTest(@TempDir Path tmp) throws Exception {
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
