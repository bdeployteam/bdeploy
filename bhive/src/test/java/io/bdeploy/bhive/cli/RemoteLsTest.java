package io.bdeploy.bhive.cli;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.bhive.cli.ImportTool;
import io.bdeploy.bhive.cli.ManifestTool;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
public class RemoteLsTest {

    @RegisterExtension
    private final TestCliTool tools = new TestCliTool(new BHiveCli());

    @Test
    public void testRemoteLs(@TempDir Path tmp) throws Exception {
        Path smallSrcDir = ContentHelper.genSimpleTestTree(tmp, "src");
        Path hive = tmp.resolve("hive");

        tools.getTool(ImportTool.class, "--hive=" + hive, "--manifest=app:v1", "--source=" + smallSrcDir).run();

        ManifestTool tool = tools.getTool(ManifestTool.class, "--remote=" + hive.toUri(), "--list");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos)) {
            tool.setOutput(ps);
            tool.run();

            assertThat(baos.toString(), startsWith("app:v1"));
        }
    }

}
