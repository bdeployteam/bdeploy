package io.bdeploy.ui.cli;

import java.time.Instant;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.cli.RemoteUserTool.UserConfig;

/**
 * Manages users.
 */
@Help("Manage (configuration UI) users on a master")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-user")
public class RemoteUserTool extends RemoteServiceTool<UserConfig> {

    public @interface UserConfig {

        @Help("Adds a user with the given name.")
        String add();

        @Help("Updates a user with the given name")
        String update();

        @Help("The password for the user to add.")
        String password();

        @Help(value = "Add global admin permission to the user. Shortcut for --permission=ADMIN", arg = false)
        boolean admin() default false;

        @Help("Add a specific permission to the user. Values can be READ, WRITE or ADMIN. Use in conjunction with --scope, otherwise permission is global.")
        String permission();

        @Help("Removes a specific permission from the user. Values can be READ, WRITE or ADMIN. Use in conjunction with --scope, otherwise permission is global.")
        String removePermission();

        @Help("Scopes a specific permission specified with --permission to a certain instance group")
        String scope();

        @Help(value = "Mark user as active during add/update", arg = false)
        boolean active() default false;

        @Help(value = "Mark user as inactive during add/update", arg = false)
        boolean inactive() default false;

        @Help("The name of the user to remove.")
        String remove();

        @Help(value = "When given, list all known users.", arg = false)
        boolean list() default false;

        @Help("Creates a token with the privileges of the given user.")
        String createToken();
    }

    public RemoteUserTool() {
        super(UserConfig.class);
    }

    @Override
    protected RenderableResult run(UserConfig config, RemoteService remote) {
        AuthResource auth = ResourceProvider.getResource(remote, AuthResource.class, getLocalContext());
        AuthAdminResource admin = auth.getAdmin();

        if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("User accounts on " + remote.getUri());

            table.column(new DataTableColumn.Builder("Username").setMinWidth(15).build());
            table.column(new DataTableColumn.Builder("System").setMinWidth(10).build());
            table.column(new DataTableColumn.Builder("Inact.").setMinWidth(6).build());
            table.column(new DataTableColumn.Builder("E-Mail").setMinWidth(10).build());
            table.column(new DataTableColumn.Builder("Last Active Login").setMinWidth(5).build());
            table.column(new DataTableColumn.Builder("Permissions").setMinWidth(20).build());

            for (UserInfo info : admin.getAllUser()) {
                table.row().cell(info.name).cell(info.externalSystem).cell(info.inactive ? "*" : "").cell(info.email)
                        .cell(FormatHelper.formatTemporal(Instant.ofEpochMilli(info.lastActiveLogin)))
                        .cell(info.permissions.toString()).build();
            }
            return table;
        } else if (config.createToken() != null) {
            createToken(config, auth);
            return null; // special output
        } else if (config.add() != null) {
            addUser(config, admin);
        } else if (config.update() != null) {
            if (config.permission() != null && config.removePermission() != null) {
                helpAndFail("Cannot add and remove a permission simultaneously");
            }
            updateUser(config, admin);
        } else if (config.remove() != null) {
            admin.deleteUser(config.remove());
        } else {
            return createNoOp();
        }
        return createSuccess();
    }

    private void createToken(UserConfig config, AuthResource auth) {
        String token = auth.getAuthPack(config.createToken(), Boolean.TRUE);

        out().println("Generating token with 50 years validity for " + config.createToken());
        out().println("Use the following token to remotely access this server in your name");
        out().println("Attention: This token is sensitive information as it allows remote access under your name. "
                + "Do not pass this token on to others.");
        out().println("");
        out().println(token);
        out().println("");
    }

    private void updateUser(UserConfig config, AuthAdminResource admin) {
        UserInfo user = admin.getUser(config.update());
        if (user == null) {
            out().println("Cannot find user " + config.update());
            return;
        }
        if (config.password() != null) {
            admin.updateLocalUserPassword(config.update(), config.password());
        }
        boolean updated = false;
        if (config.active() || config.inactive()) {
            if (setInactive(user, config)) {
                updated = true;
            }
        }
        if (config.admin()) {
            if (user.permissions.add(ScopedPermission.GLOBAL_ADMIN)) {
                updated = true;
            }
        }
        if (config.permission() != null) {
            if (user.permissions
                    .add(new ScopedPermission(config.scope(), Permission.valueOf(config.permission().toUpperCase())))) {
                updated = true;
            }
        }
        if (config.removePermission() != null) {
            if (user.permissions
                    .remove(new ScopedPermission(config.scope(), Permission.valueOf(config.removePermission().toUpperCase())))) {
                updated = true;
            }
        }
        if (updated) {
            admin.updateUser(user);
        }
    }

    private void addUser(UserConfig config, AuthAdminResource admin) {
        String userString = config.add();
        UserInfo user = new UserInfo(userString);

        setInactive(user, config);
        if (config.admin()) {
            user.permissions.add(ScopedPermission.GLOBAL_ADMIN);
        }
        if (config.permission() != null) {
            user.permissions.add(new ScopedPermission(config.scope(), Permission.valueOf(config.permission().toUpperCase())));
        }

        char[] pass;
        if (config.password() != null) {
            pass = config.password().toCharArray();
        } else {
            out().println("Please specify password for " + userString + ':');
            pass = System.console().readPassword();
        }

        user.password = new String(pass);
        admin.createLocalUser(user);
    }

    private boolean setInactive(UserInfo user, UserConfig config) {
        if (config.active() && config.inactive()) {
            helpAndFail("Cannot mark user as both active and inactive");
        }
        if (config.active() && user.inactive) {
            user.inactive = false;
            return true;
        }
        if (config.inactive() && !user.inactive) {
            user.inactive = true;
            return true;
        }
        return false;
    }
}
