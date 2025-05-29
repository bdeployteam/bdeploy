package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    protected Map<String, TestCliTool.StructuredOutputRow> doRemoteAndIndexOutputOn(
            Function<TestCliTool.StructuredOutputRow, String> indexFunction, RemoteService remote, Class<? extends CliTool> tool,
            String... args) {
        StructuredOutput output = remote(remote, tool, args);
        return IntStream.range(0, output.size()).boxed()
                .collect(Collectors.toMap(i -> indexFunction.apply(output.get(i)), output::get));
    }

    protected Map<String, TestCliTool.StructuredOutputRow> doRemoteAndIndexOutputOn(String indexColumn, RemoteService remote,
            Class<? extends CliTool> tool, String... args) {
        return doRemoteAndIndexOutputOn(s -> s.get(indexColumn), remote, tool, args);
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
        assertEquals("2", output.get(0).get("NoOfInstanceTemplates"));
    }

    protected void uploadProduct(RemoteService remote, Path tmp, Path bhivePath, TestProductFactory.TestProductDescriptor product)
            throws IOException {
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        TestProductFactory.writeProductToFile(productPath, product);

        // Push the product
        remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath, "--import=" + productPath,
                "--push");

        ProductManifest.invalidateAllScanCaches();

        StructuredOutput output = remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath,
                "--list");
        assertEquals(1, output.size());
        assertEquals("Test Product", output.get(0).get("Name"));
        assertEquals(String.valueOf(product.instanceTemplates.size()), output.get(0).get("NoOfInstanceTemplates"));
    }

}
