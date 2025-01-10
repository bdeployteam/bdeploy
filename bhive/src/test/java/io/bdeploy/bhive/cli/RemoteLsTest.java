package io.bdeploy.bhive.cli;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestCliTool;

class RemoteLsTest {

    @RegisterExtension
    private final TestCliTool tools = new TestCliTool(new BHiveCli());

    @Test
    void testRemoteLs(@TempDir Path tmp) throws Exception {
        Path smallSrcDir = ContentHelper.genSimpleTestTree(tmp, "src");
        Path hive = tmp.resolve("hive");

        tools.execute(InitTool.class, "--hive=" + hive);
        tools.execute(ImportTool.class, "--hive=" + hive, "--manifest=app:v1", "--source=" + smallSrcDir);

        var output = tools.execute(ManifestTool.class, "--remote=" + hive.toUri(), "--list");
        assertThat(output.get(0).get("Key"), is(equalTo("app:v1")));
    }

}
