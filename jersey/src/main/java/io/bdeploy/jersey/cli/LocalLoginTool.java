package io.bdeploy.jersey.cli;

import java.util.Map;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.RemoteValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.jersey.cli.LocalLoginTool.LoginConfig;

@Help("Manage local login sessions")
@ToolCategory("Local session and scripting commands")
@CliName("login")
public class LocalLoginTool extends ConfiguredCliTool<LoginConfig> {

    @Help("Configuration for remote access")
    public @interface LoginConfig {

        @Help("URI of remote Server")
        @EnvironmentFallback("BDEPLOY_REMOTE")
        @Validator(RemoteValidator.class)
        String remote();

        @Help("Perform a login to the given remote and store the session locally using the given name")
        String add();

        @Help("User to login, read from console if not given")
        String user();

        @Help("Password to use when logging in, read from console if not given")
        String password();

        @Help("Remove the given stored login session")
        String remove();

        @Help(value = "List all stored login sessions", arg = false)
        boolean list() default false;

        @Help("The name of the stored login session to switch to")
        String use();
    }

    public LocalLoginTool() {
        super(LoginConfig.class);
    }

    @Override
    protected RenderableResult run(LoginConfig config) {
        LocalLoginManager llm = new LocalLoginManager();

        if (config.add() != null) {
            addUser(config, llm);
        } else if (config.remove() != null) {
            llm.remove(config.remove());
        } else if (config.use() != null) {
            llm.setCurrent(config.use());
        } else if (config.list()) {
            LocalLoginData data = llm.read();

            DataTable table = createDataTable();
            table.column("Name", 15).column("URI", 40).column("User", 20).column("Active", 6);
            for (Map.Entry<String, LocalLoginServer> entry : data.servers.entrySet()) {
                table.row().cell(entry.getKey()).cell(entry.getValue().url).cell(entry.getValue().user)
                        .cell((data.current != null && data.current.equals(entry.getKey())) ? "*" : "").build();
            }
            return table;
        } else {
            return createNoOp();
        }

        return createSuccess();
    }

    private void addUser(LoginConfig config, LocalLoginManager llm) {
        helpAndFailIfMissing(config.remote(), "Missing --remote");

        if (config.user() == null || config.password() == null) {
            out().println("Please specify user and password for " + config.remote());
        }

        String user;
        if (config.user() != null) {
            user = config.user();
        } else {
            out().print("User: ");
            user = System.console().readLine();
        }

        char[] pass;
        if (config.password() != null) {
            pass = config.password().toCharArray();
        } else {
            out().print("Password: ");
            pass = System.console().readPassword();
        }

        llm.login(config.add(), config.remote(), user, new String(pass));
    }

}
