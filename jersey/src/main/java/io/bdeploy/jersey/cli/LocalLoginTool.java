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

    private static final String LIST_FORMAT = "%1$-20s %2$-30s %3$s";

    @Help("Configuration for remote access")
    public @interface LoginConfig {

        @Help("URI of remote Server")
        @EnvironmentFallback("BDEPLOY_REMOTE")
        @Validator(RemoteValidator.class)
        String remote();

        @Help(value = "Perform a login to the given remote and store the session locally", arg = false)
        boolean login() default false;

        @Help(value = "Remove a stored login session", arg = false)
        boolean remove() default false;

        @Help(value = "List all stored login sessions", arg = false)
        boolean list() default false;

        @Help("The name of the stored login session when logging in or removing")
        String name();

        @Help("The name of the stored login session to switch to")
        String use();
    }

    public LocalLoginTool() {
        super(LoginConfig.class);
    }

    @Override
    protected void run(LoginConfig config) {
        LocalLoginManager llm = new LocalLoginManager();

        if (config.login()) {
            helpAndFailIfMissing(config.remote(), "Missing --remote");
            helpAndFailIfMissing(config.name(), "Missing --name");

            out().println("Please specify user and password for " + config.remote());

            out().print("User: ");
            String user = System.console().readLine();

            out().print("Password: ");
            char[] pass = System.console().readPassword();

            llm.login(config.name(), config.remote(), user, new String(pass));
        } else if (config.remove()) {
            helpAndFailIfMissing(config.name(), "Missing --name");
            llm.remove(config.name());
        } else if (config.use() != null) {
            helpAndFailIfMissing(config.name(), "Missing --name");
            llm.setCurrent(config.name());
        } else if (config.list()) {
            LocalLoginData data = llm.read();
            out().println(String.format(LIST_FORMAT, "Name", "URI", "Current"));
            for (Map.Entry<String, LocalLoginServer> entry : data.servers.entrySet()) {
                out().println(String.format(LIST_FORMAT, entry.getKey(), entry.getValue().url,
                        (data.current != null && data.current.equals(entry.getKey())) ? "*" : ""));
            }
        } else {
            out().println("No action given...");
        }
    }

}
