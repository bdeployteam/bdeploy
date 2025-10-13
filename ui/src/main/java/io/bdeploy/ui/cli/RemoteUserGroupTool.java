package io.bdeploy.ui.cli;

import java.time.Instant;
import java.util.List;

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
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.cli.RemoteUserGroupTool.UserGroupConfig;

/**
 * Manages users.
 */
@Help("Manage (configuration UI) users groups on a master")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-user-group")
public class RemoteUserGroupTool extends RemoteServiceTool<UserGroupConfig> {

    public @interface UserGroupConfig {

        @Help("Adds a user group with the given name.")
        String add();

        @Help("Updates a user group with the given name")
        String update();

        @Help("Set description for user group during add/update operation")
        String description();

        @Help(value = "Add global admin permission to the user group. Shortcut for --permission=ADMIN", arg = false)
        boolean admin() default false;

        @Help(value = "Mark user group as active during add/update", arg = false)
        boolean active() default false;

        @Help(value = "Mark user group as inactive during add/update", arg = false)
        boolean inactive() default false;

        @Help("Add a specific permission to the user group. Values can be READ, WRITE or ADMIN. Use in conjunction with --scope to, otherwise permission is global.")
        String permission();

        @Help("Scopes a specific permission specified with --permission to a certain instance group or software repository")
        String scope();

        @Help("The name of the user group to remove.")
        String remove();

        @Help(value = "When given, list all known users groups.", arg = false)
        boolean list() default false;

        @Help("The name of the user group to list users for")
        String listUsers();

        @Help("The name of the user to add to the user group")
        String addUser();

        @Help("The name of the user to remove from the user group")
        String removeUser();

        @Help("Name of the user group you want to add/remove user to/from")
        String group();
    }

    public RemoteUserGroupTool() {
        super(UserGroupConfig.class);
    }

    @Override
    protected RenderableResult run(UserGroupConfig config, RemoteService remote) {
        AuthResource auth = ResourceProvider.getResource(remote, AuthResource.class, getLocalContext());
        AuthAdminResource admin = auth.getAdmin();

        if (config.add() != null) {
            add(config, admin);
        } else if (config.update() != null) {
            update(config, admin);
        } else if (config.remove() != null) {
            remove(config, admin);
        } else if (config.list()) {
            return list(admin, remote);
        } else if (config.listUsers() != null) {
            return listUsers(config, admin);
        } else if (config.addUser() != null) {
            addUser(config, admin);
        } else if (config.removeUser() != null) {
            removeUser(config, admin);
        } else {
            return createNoOp();
        }
        return createSuccess();
    }

    private void add(UserGroupConfig config, AuthAdminResource admin) {
        UserGroupInfo group = new UserGroupInfo();
        group.name = config.add();
        edit(group, config);
        admin.createUserGroup(group);
    }

    private void update(UserGroupConfig config, AuthAdminResource admin) {
        UserGroupInfo group = getGroup(config.update(), admin);
        if (group == null) {
            out().println("Cannot find user " + config.update());
            return;
        }
        edit(group, config);
        admin.updateUserGroup(group);
    }

    private void edit(UserGroupInfo group, UserGroupConfig config) {
        if (config.active() && config.inactive()) {
            helpAndFail("Cannot mark user group as both active and inactive");
        }
        if (config.active()) {
            group.inactive = false;
        }
        if (config.inactive()) {
            group.inactive = true;
        }
        if (config.description() != null) {
            group.description = config.description();
        }
        if (config.admin()) {
            group.permissions.add(ScopedPermission.GLOBAL_ADMIN);
        }
        if (config.permission() != null) {
            group.permissions.add(new ScopedPermission(config.scope(), Permission.valueOf(config.permission().toUpperCase())));
        }
    }

    private static void remove(UserGroupConfig config, AuthAdminResource admin) {
        UserGroupInfo group = getGroup(config.remove(), admin);
        admin.deleteUserGroups(group.id);
    }

    private DataTable list(AuthAdminResource admin, RemoteService remote) {
        DataTable table = createDataTable();
        table.setCaption("User group accounts on " + remote.getUri());

        table.column(new DataTableColumn.Builder("Name").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Inact.").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("Permissions").setMinWidth(20).build());

        for (UserGroupInfo info : admin.getAllUserGroups()) {
            table.row().cell(info.name).cell(info.inactive ? "*" : "").cell(info.description).cell(info.permissions.toString())
                    .build();
        }
        return table;
    }

    private DataTable listUsers(UserGroupConfig config, AuthAdminResource admin) {
        UserGroupInfo group = getGroup(config.listUsers(), admin);
        List<UserInfo> users = admin.getAllUser().stream().filter(user -> user.getGroups().contains(group.id)).toList();

        DataTable table = createDataTable();
        table.setCaption("User accounts that belong to group " + group.name);
        table.column(new DataTableColumn.Builder("Username").setMinWidth(8).build());
        table.column(new DataTableColumn.Builder("System").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Inact.").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("E-Mail").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Last Active Login").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Permissions").setMinWidth(20).build());

        for (UserInfo info : users) {
            table.row().cell(info.name).cell(info.externalSystem).cell(info.inactive ? "*" : "").cell(info.email)
                    .cell(FormatHelper.formatTemporal(Instant.ofEpochMilli(info.lastActiveLogin)))
                    .cell(info.permissions.toString()).build();
        }
        return table;
    }

    private void addUser(UserGroupConfig config, AuthAdminResource admin) {
        helpAndFailIfMissing(config.group(), "Missing --group");
        UserGroupInfo group = getGroup(config.group(), admin);
        admin.addUserToGroup(group.id, config.addUser());
    }

    private void removeUser(UserGroupConfig config, AuthAdminResource admin) {
        helpAndFailIfMissing(config.group(), "Missing --group");
        UserGroupInfo group = getGroup(config.group(), admin);
        admin.removeUserFromGroup(group.id, config.removeUser());
    }

    private static UserGroupInfo getGroup(String name, AuthAdminResource admin) {
        return admin.getAllUserGroups().stream().filter(g -> name.equalsIgnoreCase(g.name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + name));
    }

}
