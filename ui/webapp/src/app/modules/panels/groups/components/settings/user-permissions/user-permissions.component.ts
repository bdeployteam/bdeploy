import { Component, Input, TemplateRef, ViewChild, inject } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { Permission, UserInfo } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
  BdDialogMessageAction,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { getInheritedPermission, getInheritedPermissionHint } from 'src/app/modules/core/utils/permission.utils';
import { GroupUsersService } from 'src/app/modules/panels/groups/services/group-users.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { BdDataPermissionLevelCellComponent } from '../../../../../core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { UsersColumnsService } from '../../../../../core/services/users-columns.service';

@Component({
  selector: 'app-instance-group-user-permissions',
  templateUrl: './user-permissions.component.html',
})
export class UserPermissionsComponent {
  protected groups = inject(GroupsService);
  protected users = inject(GroupUsersService);
  protected userCols = inject(UsersColumnsService);

  private readonly colInheritedPerm: BdDataColumn<UserInfo> = {
    id: 'inherited',
    name: 'Inherited Perm.',
    data: (r) => this.getInheritedPermissionLevel(r),
    width: '100px',
    tooltip: (r) => this.getInheritedPermissionTooltip(r),
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colGlobalPerm: BdDataColumn<UserInfo> = {
    id: 'global',
    name: 'Global Perm.',
    data: (r) => this.getGlobalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colLocalPerm: BdDataColumn<UserInfo> = {
    id: 'local',
    name: 'Local Perm.',
    data: (r) => this.getLocalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colModPerm: BdDataColumn<UserInfo> = {
    id: 'modify',
    name: 'Modify',
    data: (r) => `Modify permissions for ${r.name}`,
    action: (r) => this.doModify(this.modDialog, r),
    actionDisabled: (r) => this.getAvailablePermissionsForUser(r).length === 0,
    icon: (r) => (!this.getLocalPermissionLevel(r) && !this.getGlobalPermissionLevel(r) ? 'add' : 'edit'),
    width: '40px',
  };

  private readonly actRemoveLocal: BdDialogMessageAction<any> = {
    name: 'Remove',
    result: 'REMOVE',
    confirm: false,
    disabled: () => !this.getLocalPermissionLevel(this.modUser),
  };

  private groupNames = ['Local Permission', 'Global Permission', 'No Permission'];

  protected columns: BdDataColumn<UserInfo>[] = [
    ...this.userCols.defaultUsersColumns,
    this.colInheritedPerm,
    this.colGlobalPerm,
    this.colLocalPerm,
    this.colModPerm,
  ];
  protected grouping: BdDataGrouping<UserInfo>[] = [
    {
      definition: {
        group: (r) =>
          !this.getLocalPermissionLevel(r)
            ? !this.getGlobalPermissionLevel(r)
              ? this.groupNames[2]
              : this.groupNames[1]
            : this.groupNames[0],
        name: 'Permission Type',
        sort: (a, b) => this.groupNames.indexOf(a) - this.groupNames.indexOf(b),
      },
      selected: [],
    },
  ];

  protected modPerm: Permission;
  protected modUser: UserInfo;
  protected availablePermissionsForUser: Permission[];

  @Input() dialog: BdDialogComponent;
  @ViewChild('modDialog') private modDialog: TemplateRef<any>;

  private getInheritedPermissionTooltip(user: UserInfo): string {
    const scope = this.groups.current$.value.name;
    const userGroups = this.users.userGroups$.value;
    return getInheritedPermissionHint(user, userGroups, scope);
  }

  private getInheritedPermissionLevel(user: UserInfo): Permission {
    const scope = this.groups.current$.value.name;
    const userGroups = this.users.userGroups$.value;
    return getInheritedPermission(user, userGroups, scope);
  }

  private getLocalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => p.scope === this.groups.current$.value.name)?.permission;
  }

  private getGlobalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => !p.scope)?.permission;
  }

  private doModify(tpl: TemplateRef<any>, user: UserInfo) {
    this.modUser = user;
    this.availablePermissionsForUser = this.getAvailablePermissionsForUser(this.modUser);
    this.modPerm = this.getLocalPermissionLevel(user);
    this.dialog
      .message({
        header: `Modify ${user.name} permissions`,
        template: tpl,
        actions: [this.actRemoveLocal, ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (r === this.actRemoveLocal.result) {
          this.users.updatePermission(user, null).subscribe();
        } else if (r) {
          this.users.updatePermission(user, this.modPerm).subscribe();
        }
      });
  }

  private getAvailablePermissionsForUser(user: UserInfo): Permission[] {
    if (!user) {
      return [];
    }

    // only permissions HIGHER than the current global permission are avilable.
    const glob = this.getGlobalPermissionLevel(user);
    const allPerms = [Permission.CLIENT, Permission.READ, Permission.WRITE, Permission.ADMIN];

    if (!glob) return allPerms;
    if (glob === Permission.CLIENT) return allPerms.slice(1);
    if (glob === Permission.READ) return allPerms.slice(2);
    if (glob === Permission.WRITE) return allPerms.slice(3);

    return [];
  }
}
