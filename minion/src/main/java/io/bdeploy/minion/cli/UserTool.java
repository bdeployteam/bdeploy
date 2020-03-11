package io.bdeploy.minion.cli;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.UserTool.UserConfig;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;

/**
 * Manages users.
 */
@Help("Manage (configuration UI) users on a master.")
@CliName("user")
public class UserTool extends RemoteServiceTool<UserConfig> {

    public @interface UserConfig {

        @Help("Adds a user with the given name.")
        String add();

        @Help("Updates a user with the given name")
        String update();

        @Help("The password for the user to add.")
        String password();

        @Help("Add global admin permission to the user. Shortcut for --permission=ADMIN")
        boolean admin() default false;

        @Help("Add a specific permission to the user. Values can be READ, WRITE or ADMIN. Use in conjunction with --scope to, otherwise permission is global.")
        String permission();

        @Help("Scopes a specific permission specified with --permission to a certain instance group")
        String scope();

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
    protected void run(UserConfig config, RemoteService remote) {
        AuthResource auth = ResourceProvider.getResource(remote, AuthResource.class, null);
        AuthAdminResource admin = auth.getAdmin();

        if (config.add() != null) {
            UserInfo user = new UserInfo(config.add());
            if (config.admin()) {
                user.permissions.add(ApiAccessToken.ADMIN_PERMISSION);
            }
            if (config.permission() != null) {
                user.permissions.add(new ScopedPermission(config.scope(), Permission.valueOf(config.permission().toUpperCase())));
            }
            user.password = config.password();
            admin.createLocalUser(user);
        } else if (config.update() != null) {
            UserInfo user = admin.getUser(config.update());
            if (user == null) {
                out().println("Cannot find user " + config.update());
                return;
            }
            if (config.password() != null) {
                admin.updateLocalUserPassword(config.update(), config.password());
            }
            if (config.admin()) {
                user.permissions.add(ApiAccessToken.ADMIN_PERMISSION);
                admin.updateUser(user);
            }
            if (config.permission() != null) {
                user.permissions.add(new ScopedPermission(config.scope(), Permission.valueOf(config.permission().toUpperCase())));
                admin.updateUser(user);
            }
        } else if (config.remove() != null) {
            admin.deleteUser(config.remove());
        } else if (config.list()) {
            String formatString = "%1$-30s %2$-8s %3$-8s %4$-30s";
            out().println(String.format(formatString, "Username", "External", "Inactive", "Permissions"));
            for (UserInfo info : admin.getAllUser()) {
                out().println(String.format(formatString, info.name, info.external, info.inactive, info.permissions));
            }
        } else if (config.createToken() != null) {
            String token = auth.getAuthPack(config.createToken(), true);

            out().println("Generating token with 50 years validity for " + config.createToken());
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
