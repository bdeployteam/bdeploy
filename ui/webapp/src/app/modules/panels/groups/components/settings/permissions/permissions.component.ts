import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { Permission, UserInfo } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_OK, BdDialogMessageAction } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { UsersColumnsService } from '../../../services/users-columns.service';
import { UsersService } from '../../../services/users.service';
import { PermissionLevelComponent } from './permission-level/permission-level.component';

@Component({
  selector: 'app-permissions',
  templateUrl: './permissions.component.html',
  styleUrls: ['./permissions.component.css'],
})
export class PermissionsComponent implements OnInit, OnDestroy {
  private readonly colGlobalPerm: BdDataColumn<UserInfo> = {
    id: 'global',
    name: 'Global Perm.',
    data: (r) => this.getGlobalPermissionLevel(r),
    width: '100px',
    component: PermissionLevelComponent,
  };

  private readonly colLocalPerm: BdDataColumn<UserInfo> = {
    id: 'local',
    name: 'Local Perm.',
    data: (r) => this.getLocalPermissionLevel(r),
    width: '100px',
    component: PermissionLevelComponent,
  };

  private readonly colModPerm: BdDataColumn<UserInfo> = {
    id: 'modify',
    name: 'Modify',
    data: (r) => `Modify permissions for ${r.name}`,
    action: (r) => this.doModify(this.modDialog, r),
    icon: (r) => (!this.getLocalPermissionLevel(r) && !this.getGlobalPermissionLevel(r) ? 'add' : 'edit'),
    width: '40px',
  };

  private readonly actRemoveLocal: BdDialogMessageAction<any> = {
    name: 'REMOVE',
    result: 'REMOVE',
    confirm: false,
    disabled: () => !this.getLocalPermissionLevel(this.modUser),
  };

  private groupNames = ['Local Permission', 'Global Permission', 'No Permission'];

  /* template */ columns: BdDataColumn<UserInfo>[] = [...this.userCols.defaultUsersColumns, this.colGlobalPerm, this.colLocalPerm, this.colModPerm];
  /* template */ grouping: BdDataGrouping<UserInfo>[] = [
    {
      definition: {
        group: (r) => (!this.getLocalPermissionLevel(r) ? (!this.getGlobalPermissionLevel(r) ? this.groupNames[2] : this.groupNames[1]) : this.groupNames[0]),
        name: 'Permission Type',
        sort: (a, b) => this.groupNames.indexOf(a) - this.groupNames.indexOf(b),
      },
      selected: [],
    },
  ];

  /* template */ modPerm: Permission;
  /* template */ modUser: UserInfo;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('modDialog') private modDialog: TemplateRef<any>;

  constructor(public groups: GroupsService, public users: UsersService, public userCols: UsersColumnsService) {}

  ngOnInit(): void {}

  ngOnDestroy(): void {}

  private getLocalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => p.scope === this.groups.current$.value.name)?.permission;
  }

  private getGlobalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => !p.scope)?.permission;
  }

  private doModify(tpl: TemplateRef<any>, user: UserInfo) {
    this.modUser = user;
    this.modPerm = this.getLocalPermissionLevel(user);
    this.dialog
      .message({ header: `Modify ${user.name} permissions`, template: tpl, actions: [this.actRemoveLocal, ACTION_CANCEL, ACTION_OK] })
      .subscribe((r) => {
        if (r === this.actRemoveLocal.result) {
          this.users.updatePermission(user, this.groups.current$.value.name, null).subscribe();
        } else if (r) {
          this.users.updatePermission(user, this.groups.current$.value.name, this.modPerm).subscribe();
        }
      });
  }

  /* template */ getAvailablePermissionsFor(user: UserInfo): Permission[] {
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
