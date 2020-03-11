package io.bdeploy.jersey.cli;

import java.util.Map;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.RemoteValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.jersey.cli.LocalLoginTool.LoginConfig;

@Help("Manage local login sessions")
@CliName("login")
public class LocalLoginTool extends ConfiguredCliTool<LoginConfig> {

    private static final String LIST_FORMAT = "%1$-20s %2$-30s %3$-15s %4$s";

    @Help("Configuration for remote access")
    public @interface LoginConfig {

        @Help("URI of remote Server")
        @EnvironmentFallback("BDEPLOY_REMOTE")
        @Validator(RemoteValidator.class)
        String remote();

        @Help("Perform a login to the given remote and store the session locally using the given name")
        String add();

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
    protected void run(LoginConfig config) {
        LocalLoginManager llm = new LocalLoginManager();

        if (config.add() != null) {
            helpAndFailIfMissing(config.remote(), "Missing --remote");

            out().println("Please specify user and password for " + config.remote());

            out().print("User: ");
            String user = System.console().readLine();

            out().print("Password: ");
            char[] pass = System.console().readPassword();

            llm.login(config.add(), config.remote(), user, new String(pass));
        } else if (config.remove() != null) {
            llm.remove(config.remove());
        } else if (config.use() != null) {
            llm.setCurrent(config.use());
        } else if (config.list()) {
            LocalLoginData data = llm.read();
            out().println(String.format(LIST_FORMAT, "Name", "URI", "User", "Current"));
            for (Map.Entry<String, LocalLoginServer> entry : data.servers.entrySet()) {
                out().println(String.format(LIST_FORMAT, entry.getKey(), entry.getValue().url, entry.getValue().user,
                        (data.current != null && data.current.equals(entry.getKey())) ? "*" : ""));
            }
        } else {
            out().println("No action given...");
        }
    }

}
