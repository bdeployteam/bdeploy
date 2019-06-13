package io.bdeploy.minion.cli;

import java.nio.file.Paths;
import java.util.Collections;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.UserTool.UserConfig;

/**
 * Manages users.
 */
@Help("Manage (configuration UI) users on a master.")
@CliName("user")
public class UserTool extends ConfiguredCliTool<UserConfig> {

    public @interface UserConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        String root();

        @Help("Adds a user with the given name.")
        String add();

        @Help("Updates a user with the given name")
        String update();

        @Help("The password for the user to add.")
        String password();

        @Help("Add global admin capability to the user.")
        boolean admin() default false;

        @Help("The name of the user to remove.")
        String remove();

        @Help(value = "When given, list all known users.", arg = false)
        boolean list() default false;
    }

    public UserTool() {
        super(UserConfig.class);
    }

    @Override
    protected void run(UserConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            if (config.add() != null || config.update() != null) {
                String user = config.add() != null ? config.add() : config.update();
                r.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration())
                        .clobberParameter("password").setWhat(config.add() != null ? "add-user" : "update-user").build());
                r.getUsers().updateUser(user, config.password(),
                        config.admin() ? Collections.singletonList(ApiAccessToken.ADMIN_CAPABILITY) : null);
            } else if (config.remove() != null) {
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("remove-user").build());
                r.getUsers().removeUser(config.remove());
            } else if (config.list()) {
                for (String u : r.getUsers().getAllNames()) {
                    out().println(u);
                }
            } else {
                out().println("Nothing to do, please give more arguments");
            }
        }
    }

}
