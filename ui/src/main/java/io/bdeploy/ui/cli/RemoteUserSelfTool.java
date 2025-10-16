package io.bdeploy.ui.cli;

import java.time.Instant;
import java.util.stream.Collectors;

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
import io.bdeploy.interfaces.UserChangePasswordDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.cli.RemoteUserSelfTool.UserSelfConfig;
import jakarta.ws.rs.core.Response;

/**
 * Manages the current user
 */
@Help("Manage the currently logged in user")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-user-self")
public class RemoteUserSelfTool extends RemoteServiceTool<UserSelfConfig> {

    public @interface UserSelfConfig {

        @Help(value = "Shows information about the current user", arg = false)
        boolean info() default false;

        @Help(value = "Update information of the current user", arg = false)
        boolean update() default false;

        @Help("Used in combination with --update to set the full name of the current user")
        String fullName();

        @Help("Used in combination with --update to set the email of the current user")
        String email();

        @Help(value = "Update the password of the current user", arg = false)
        boolean updatePassword() default false;

        @Help("Used in combination with --updatePassword to change the password of the current user")
        String currentPassword();

        @Help("Used in combination with --updatePassword to change the password of the current user")
        String newPassword();

        @Help("Removes the current user from the given group")
        String leaveGroup();

        @Help("Remove the given permission from the current user. Accepted values are: READ, WRITE, ADMIN.")
        String removePermission();

        @Help("Used in combination with --removePermission to define the scopes of the permission to be removed. If unset, a global scope is assumed.")
        String scope();

        @Help("Used to deactivate the current user")
        boolean deactivate() default false;

        @Help(value = "Deletes the current user", arg = false)
        boolean delete() default false;

        @Help(value = "Creates a token for the current user", arg = false)
        boolean createToken() default false;
    }

    public RemoteUserSelfTool() {
        super(UserSelfConfig.class);
    }

    @Override
    protected RenderableResult run(UserSelfConfig config, RemoteService remote) {
        AuthResource auth = ResourceProvider.getResource(remote, AuthResource.class, getLocalContext());
        if (config.info()) {
            return info(remote, auth);
        } else if (config.update()) {
            return update(config, remote, auth);
        } else if (config.updatePassword()) {
            helpAndFailIfMissing(config.currentPassword(), "current password must be given");
            helpAndFailIfMissing(config.newPassword(), "new password must be given");
            return updatePassword(config, remote, auth);
        } else if (config.leaveGroup() != null) {
            return leaveGroup(config, remote, auth);
        } else if (config.removePermission() != null) {
            return removePermission(config, remote, auth);
        } else if (config.deactivate()) {
            return deactivate(remote, auth);
        } else if (config.delete()) {
            return delete(remote, auth);
        } else if (config.createToken()) {
            createToken(config, auth);
            return null; // special output
        }
        return createNoOp();
    }

    private RenderableResult info(RemoteService remote, AuthResource auth) {
        var info = auth.getCurrentUser();
        var profileInfo = auth.getCurrentUserProfile();

        if (info == null || profileInfo == null) {
            return createResultWithErrorMessage("The current user could not be found");
        }

        DataTable table = createDataTable();
        table.setCaption("Information about: " + info.name);
        table.column(new DataTableColumn.Builder("Username").setMinWidth(15).build());
        table.column(new DataTableColumn.Builder("Full Name").setMinWidth(15).build());
        table.column(new DataTableColumn.Builder("E-Mail").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("External System").setMinWidth(12).build());
        table.column(new DataTableColumn.Builder("Inactive").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Last Active Login").setMinWidth(8).build());
        table.column(new DataTableColumn.Builder("Permissions").setMinWidth(11).build());
        table.column(new DataTableColumn.Builder("Groups").setMinWidth(6).build());

        String groups = profileInfo.userGroups.stream().map(g -> g.name + " (" + g.description + ')')
                .collect(Collectors.joining("; "));

        table.row()//
                .cell(info.name)//
                .cell(info.fullName)//
                .cell(info.email)//
                .cell(info.externalSystem)//
                .cell(info.inactive ? "*" : "")//
                .cell(FormatHelper.formatTemporal(Instant.ofEpochMilli(info.lastActiveLogin)))//
                .cell(info.permissions.toString())//
                .cell(groups)//
                .build();

        return table;
    }

    private RenderableResult update(UserSelfConfig config, RemoteService remote, AuthResource auth) {
        UserInfo user = auth.getCurrentUser();
        if (user == null) {
            return createResultWithErrorMessage("The current user could not be found");
        }

        boolean anyChange = false;
        String fullName = config.fullName();
        if (fullName != null) {
            user.fullName = fullName;
            anyChange = true;
        }
        String email = config.email();
        if (email != null) {
            user.email = email;
            anyChange = true;
        }
        if (anyChange) {
            auth.updateCurrentUser(null, user);
            return createSuccess();
        }
        return createResultWithSuccessMessage("No change");
    }

    private RenderableResult updatePassword(UserSelfConfig config, RemoteService remote, AuthResource auth) {
        UserInfo user = auth.getCurrentUser();
        if (user == null) {
            return createResultWithErrorMessage("The current user could not be found");
        }

        UserChangePasswordDto dto = new UserChangePasswordDto(user.name, config.currentPassword(), config.newPassword());
        Response response = auth.changePassword(dto);
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            return createSuccess();
        }
        return createResultWithErrorMessage(response.getStatusInfo().getReasonPhrase());
    }

    private RenderableResult leaveGroup(UserSelfConfig config, RemoteService remote, AuthResource auth) {
        String groupName = config.leaveGroup();
        var match = auth.getCurrentUserProfile().userGroups.stream().filter(g -> g.name.equals(groupName)).findAny();
        if (match.isEmpty()) {
            return createResultWithErrorMessage("The current user is not in group " + groupName);
        }
        auth.removeCurrentUserFromGroup(match.get().id);
        return createSuccess();
    }

    private RenderableResult removePermission(UserSelfConfig config, RemoteService remote, AuthResource auth) {
        Permission toRemove;
        try {
            toRemove = ScopedPermission.Permission.valueOf(config.removePermission().toUpperCase());
        } catch (Exception e) {
            return createResultWithErrorMessage(e.getMessage());
        }

        UserInfo user = auth.getCurrentUser();
        if (user == null) {
            return createResultWithErrorMessage("The current user could not be found");
        }

        String scope = config.scope();
        boolean anyChange = (scope == null)//
                ? user.permissions.removeIf(p -> p.permission == toRemove && p.scope == null)
                : user.permissions.removeIf(p -> p.permission == toRemove && scope.equals(p.scope));
        if (anyChange) {
            auth.updateCurrentUser(null, user);
            return createSuccess();
        }
        return createResultWithSuccessMessage("No change");
    }

    private RenderableResult deactivate(RemoteService remote, AuthResource auth) {
        UserInfo user = auth.getCurrentUser();
        if (user == null) {
            return createResultWithErrorMessage("The current user could not be found");
        }
        user.inactive = true;
        auth.updateCurrentUser(null, user);
        return createSuccess();
    }

    private RenderableResult delete(RemoteService remote, AuthResource auth) {
        auth.deleteCurrentUser(null);
        return createSuccess();
    }

    private void createToken(UserSelfConfig config, AuthResource auth) {
        out().println("Generating token with 50 years validity for the current user.");
        out().println("Use the following token to remotely access this server in your name");
        out().println("Attention: This token is sensitive information as it allows remote access under your name. "
                + "Do not pass this token on to others.");
        out().println("");
        out().println(auth.getAuthPack(null, Boolean.TRUE));
        out().println("");
    }
}
