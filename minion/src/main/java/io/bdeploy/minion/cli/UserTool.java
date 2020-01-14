package io.bdeploy.minion.cli;

import java.nio.file.Paths;
import java.util.Collections;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.UserTool.UserConfig;
import io.bdeploy.minion.user.UserDatabase;

/**
 * Manages users.
 */
@Help("Manage (configuration UI) users on a master.")
@CliName("user")
public class UserTool extends ConfiguredCliTool<UserConfig> {

    public @interface UserConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
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

        @Help("Creates a token with the privileges of the given user.")
        String createToken();
    }

    public UserTool() {
        super(UserConfig.class);
    }

    @Override
    protected void run(UserConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            UserDatabase userDb = r.getUsers();
            if (config.add() != null) {
                String user = config.add();
                r.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration())
                        .clobberParameter("password").setWhat("add-user").build());
                userDb.createLocalUser(user, config.password(),
                        config.admin() ? Collections.singletonList(ApiAccessToken.ADMIN_CAPABILITY) : null);
            } else if (config.update() != null) {
                String user = config.update();
                r.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration())
                        .clobberParameter("password").setWhat("update-user").build());
                userDb.updateLocalPassword(user, config.password());
                if (config.admin()) {
                    UserInfo info = userDb.getUser(user);
                    info.capabilities.add(ApiAccessToken.ADMIN_CAPABILITY);
                    userDb.updateUserInfo(info);
                }
            } else if (config.remove() != null) {
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("remove-user").build());
                userDb.deleteUser(config.remove());
            } else if (config.list()) {
                String formatString = "%1$-30s %2$-8s %3$-8s %4$-30s";
                out().println(String.format(formatString, "Username", "External", "Inactive", "Capabilities"));
                for (UserInfo info : userDb.getAll()) {
                    out().println(String.format(formatString, info.name, info.external, info.inactive, info.capabilities));
                }
            } else if (config.createToken() != null) {
                helpAndFailIfMissing(config.password(), "Missing --password");
                UserInfo info = userDb.authenticate(config.createToken(), config.password());
                if (info == null) {
                    helpAndFail("Invalid username / password");
                }
                String token = r.createToken(info.name, info.capabilities);

                out().println("Generating token with 50 years validity for " + info.name);
                out().println("Use the following token to remotely access this server in your name");
                out().println("Attention: This token is sensitive information as it allows remote access under your name. "
                        + "Do not pass this token on to others.");
                out().println("");
                out().println(token);
                out().println("");
            } else {
                out().println("Nothing to do, please give more arguments");
            }
        }
    }

}
