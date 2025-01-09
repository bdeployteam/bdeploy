package io.bdeploy.minion.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.security.RemoteService;

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
}
