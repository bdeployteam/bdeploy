import { Component, Input, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { Permission, UserGroupInfo } from 'src/app/models/gen.dtos';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
  BdDialogMessageAction,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { UserGroupsColumnsService } from 'src/app/modules/core/services/user-groups-columns.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';

@Component({
  selector: 'app-software-repository-user-group-permissions',
  templateUrl: './user-group-permissions.component.html',
})
export class UserGroupPermissionsComponent {
  private readonly colGlobalPerm: BdDataColumn<UserGroupInfo> = {
    id: 'global',
    name: 'Global Perm.',
    data: (r) => this.getGlobalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colLocalPerm: BdDataColumn<UserGroupInfo> = {
    id: 'local',
    name: 'Local Perm.',
    data: (r) => this.getLocalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colModPerm: BdDataColumn<UserGroupInfo> = {
    id: 'modify',
    name: 'Modify',
    data: (r) => `Modify permissions for ${r.name}`,
    action: (r) => this.doModify(this.modDialog, r),
    actionDisabled: (r) => this.getAvailablePermissionsForGroup(r).length === 0,
    icon: (r) =>
      !this.getLocalPermissionLevel(r) && !this.getGlobalPermissionLevel(r)
        ? 'add'
        : 'edit',
    width: '40px',
  };

  private readonly actRemoveLocal: BdDialogMessageAction<any> = {
    name: 'Remove',
    result: 'REMOVE',
    confirm: false,
    disabled: () => !this.getLocalPermissionLevel(this.modGroup),
  };

  private groupNames = [
    'Local Permission',
    'Global Permission',
    'No Permission',
  ];

  /* template */ columns: BdDataColumn<UserGroupInfo>[] = [
    ...this.groupCols.defaultColumns,
    this.colGlobalPerm,
    this.colLocalPerm,
    this.colModPerm,
  ];
  /* template */ grouping: BdDataGrouping<UserGroupInfo>[] = [
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

  /* template */ modPerm: Permission;
  /* template */ modGroup: UserGroupInfo;
  /* template */ availablePermissionsForGroup: Permission[];

  @Input() dialog: BdDialogComponent;
  @ViewChild('modDialog') private modDialog: TemplateRef<any>;

  constructor(
    public repos: RepositoriesService,
    public users: RepositoryUsersService,
    private groupCols: UserGroupsColumnsService
  ) {}

  private getLocalPermissionLevel(group: UserGroupInfo): Permission {
    return group.permissions.find(
      (p) => p.scope === this.repos.current$.value.name
    )?.permission;
  }

  private getGlobalPermissionLevel(group: UserGroupInfo): Permission {
    return group.permissions.find((p) => !p.scope)?.permission;
  }

  private doModify(tpl: TemplateRef<any>, group: UserGroupInfo) {
    this.modGroup = group;
    this.availablePermissionsForGroup = this.getAvailablePermissionsForGroup(
      this.modGroup
    );
    this.modPerm = this.getLocalPermissionLevel(group);
    this.dialog
      .message({
        header: `Modify ${group.name} permissions`,
        template: tpl,
        actions: [this.actRemoveLocal, ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (r === this.actRemoveLocal.result) {
          this.users.updateUserGroupPermission(group, null).subscribe();
        } else if (r) {
          this.users.updateUserGroupPermission(group, this.modPerm).subscribe();
        }
      });
  }

  private getAvailablePermissionsForGroup(group: UserGroupInfo): Permission[] {
    if (!group) {
      return [];
    }

    // only permissions HIGHER than the current global permission are avilable.
    const glob = this.getGlobalPermissionLevel(group);
    const allPerms = [
      Permission.CLIENT,
      Permission.READ,
      Permission.WRITE,
      Permission.ADMIN,
    ];

    if (!glob) return allPerms;
    if (glob === Permission.CLIENT) return allPerms.slice(1);
    if (glob === Permission.READ) return allPerms.slice(2);
    if (glob === Permission.WRITE) return allPerms.slice(3);

    return [];
  }
}
