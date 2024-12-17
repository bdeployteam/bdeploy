package io.bdeploy.minion.cli;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.cli.ToolBase.CliTool;

/** Base class for all CLI tests that use a {@link MinionServerCli} */
public abstract class BaseMinionCliTest {

    @RegisterExtension
    protected TestCliTool tools = new TestCliTool(new MinionServerCli());

    protected StructuredOutput remote(URI remote, String auth, Class<? extends CliTool> tool, String... args) {
        List<String> argList = new ArrayList<>();
        argList.add("--remote=" + remote);
        argList.add("--token=" + auth);
        argList.addAll(Arrays.asList(args));
        try {
            return tools.execute(tool, argList.toArray(new String[0]));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
