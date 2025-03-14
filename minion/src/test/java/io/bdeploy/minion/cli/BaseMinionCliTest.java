package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool;

/** Base class for all CLI tests that use a {@link MinionServerCli} */
public abstract class BaseMinionCliTest {

    @RegisterExtension
    protected final TestCliTool tools = new TestCliTool(new MinionServerCli());

    protected StructuredOutput remote(RemoteService remote, Class<? extends CliTool> tool, String... args) {
        List<String> argList = new ArrayList<>();
        argList.add("--remote=" + remote.getUri());
        argList.add("--token=" + remote.getAuthPack());
        argList.addAll(Arrays.asList(args));
        try {
            return tools.execute(tool, argList.toArray(new String[0]));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void createInstanceGroup(RemoteService remote) {
        StructuredOutput output = remote(remote, RemoteInstanceGroupTool.class, "--list");
        assertEquals(0, output.size());

        remote(remote, RemoteInstanceGroupTool.class, "--create=GROUP_NAME");

        output = remote(remote, RemoteInstanceGroupTool.class, "--list");
        assertEquals(1, output.size());
        assertEquals("GROUP_NAME", output.get(0).get("Name"));
    }

    protected void uploadProduct(RemoteService remote, Path bhivePath, Path productPath) {
        // Push the product
        remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath, "--import=" + productPath,
                "--push");

        ProductManifest.invalidateAllScanCaches();

        StructuredOutput output = remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath,
                "--list");
        assertEquals(1, output.size());
        assertEquals("Test Product", output.get(0).get("Name"));
        assertEquals("1", output.get(0).get("NoOfInstanceTemplates"));
    }
}
