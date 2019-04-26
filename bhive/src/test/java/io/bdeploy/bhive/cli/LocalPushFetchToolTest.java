package io.bdeploy.bhive.cli;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.bhive.cli.ExportTool;
import io.bdeploy.bhive.cli.FetchTool;
import io.bdeploy.bhive.cli.ImportTool;
import io.bdeploy.bhive.cli.PushTool;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
public class LocalPushFetchToolTest {

    @RegisterExtension
    private final TestCliTool tools = new TestCliTool(new BHiveCli());

    @Test
    public void testPush(@TempDir Path tmp) throws Exception {
        Path src = ContentHelper.genSimpleTestTree(tmp, "source");
        Path target = tmp.resolve("target");
        Path hive1 = tmp.resolve("hive1");
        Path hive2 = tmp.resolve("hive2");

        String hive1arg = "--hive=" + hive1.toString();
        String hive2arg = "--hive=" + hive2.toString();

        tools.getTool(ImportTool.class, hive1arg, "--source=" + src, "--manifest=app:v1").run();
        tools.getTool(PushTool.class, hive1arg, "--remote=" + hive2.toUri(), "--manifest=app:v1", "--target=default").run();
        tools.getTool(ExportTool.class, hive2arg, "--target=" + target, "--manifest=app:v1").run();

        ContentHelper.checkDirsEqual(src, target);
    }

    @Test
    public void testFetch(@TempDir Path tmp) throws Exception {
        Path src = ContentHelper.genSimpleTestTree(tmp, "source");
        Path target = tmp.resolve("target");
        Path hive1 = tmp.resolve("hive1");
        Path hive2 = tmp.resolve("hive2");

        String hive1arg = "--hive=" + hive1.toString();
        String hive2arg = "--hive=" + hive2.toString();

        tools.getTool(ImportTool.class, hive1arg, "--source=" + src, "--manifest=app:v1").run();
        tools.getTool(FetchTool.class, hive2arg, "--remote=" + hive1.toUri(), "--manifest=app:v1").run();
        tools.getTool(ExportTool.class, hive2arg, "--target=" + target, "--manifest=app:v1").run();

        ContentHelper.checkDirsEqual(src, target);
    }

}
