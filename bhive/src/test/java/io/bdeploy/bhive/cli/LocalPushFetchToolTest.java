package io.bdeploy.bhive.cli;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestCliTool;

class LocalPushFetchToolTest {

    @RegisterExtension
    final TestCliTool tools = new TestCliTool(new BHiveCli());

    @Test
    void testPush(@TempDir Path tmp) throws Exception {
        Path src = ContentHelper.genSimpleTestTree(tmp, "source");
        Path target = tmp.resolve("target");
        Path hive1 = tmp.resolve("hive1");
        Path hive2 = tmp.resolve("hive2");

        String hive1arg = "--hive=" + hive1.toString();
        String hive2arg = "--hive=" + hive2.toString();

        tools.execute(InitTool.class, hive1arg);
        tools.execute(InitTool.class, hive2arg);

        tools.execute(ImportTool.class, hive1arg, "--source=" + src, "--manifest=app:v1");
        tools.execute(PushTool.class, hive1arg, "--remote=" + hive2.toUri(), "--manifest=app:v1", "--target=default");
        tools.execute(ExportTool.class, hive2arg, "--target=" + target, "--manifest=app:v1");

        ContentHelper.checkDirsEqual(src, target);
    }

    @Test
    void testFetch(@TempDir Path tmp) throws Exception {
        Path src = ContentHelper.genSimpleTestTree(tmp, "source");
        Path target = tmp.resolve("target");
        Path hive1 = tmp.resolve("hive1");
        Path hive2 = tmp.resolve("hive2");

        String hive1arg = "--hive=" + hive1.toString();
        String hive2arg = "--hive=" + hive2.toString();

        tools.execute(InitTool.class, hive1arg);
        tools.execute(InitTool.class, hive2arg);

        tools.execute(ImportTool.class, hive1arg, "--source=" + src, "--manifest=app:v1");
        tools.execute(FetchTool.class, hive2arg, "--remote=" + hive1.toUri(), "--manifest=app:v1");
        tools.execute(ExportTool.class, hive2arg, "--target=" + target, "--manifest=app:v1");

        ContentHelper.checkDirsEqual(src, target);
    }

}
