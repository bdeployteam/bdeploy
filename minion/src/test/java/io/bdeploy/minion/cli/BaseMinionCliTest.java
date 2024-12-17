package io.bdeploy.minion.cli;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TestCliTool;

/** Base class for all CLI tests that use a {@link MinionServerCli} */
public abstract class BaseMinionCliTest {

    @RegisterExtension
    protected TestCliTool tools = new TestCliTool(new MinionServerCli());
}
