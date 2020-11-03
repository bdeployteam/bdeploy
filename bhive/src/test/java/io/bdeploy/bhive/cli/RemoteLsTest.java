package io.bdeploy.bhive.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestCliTool;

@ExtendWith(TempDirectory.class)
public class RemoteLsTest {

    @RegisterExtension
    final TestCliTool tools = new TestCliTool(new BHiveCli());

    @Test
    public void testRemoteLs(@TempDir Path tmp) throws Exception {
        Path smallSrcDir = ContentHelper.genSimpleTestTree(tmp, "src");
        Path hive = tmp.resolve("hive");

        tools.execute(InitTool.class, "--hive=" + hive);
        tools.execute(ImportTool.class, "--hive=" + hive, "--manifest=app:v1", "--source=" + smallSrcDir);

        String[] output = tools.execute(ManifestTool.class, "--remote=" + hive.toUri(), "--list");
        assertThat(output[1], containsString("app:v1"));
    }

}
