import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { Permission, UserInfo } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_OK, BdDialogMessageAction } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { BdDataPermissionLevelCellComponent } from '../../../../../core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { UsersColumnsService } from '../../../../../core/services/users-columns.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';

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
    actionDisabled: (r) => this.getAvailablePermissionsFor(r).length === 0,
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

  constructor(public repos: RepositoriesService, public users: RepositoryUsersService, public userCols: UsersColumnsService) {}

  ngOnInit(): void {}

  ngOnDestroy(): void {}

  private getLocalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => p.scope === this.repos.current$.value.name)?.permission;
  }

  private getGlobalPermissionLevel(user: UserInfo): Permission {
    const p = user.permissions.find((p) => !p.scope)?.permission;
    if (!p) {
      return Permission.READ; // implicit READ permission on repos for everybody.
    }
    return p;
  }

  private doModify(tpl: TemplateRef<any>, user: UserInfo) {
    this.modUser = user;
    this.modPerm = this.getLocalPermissionLevel(user);
    this.dialog
      .message({ header: `Modify ${user.name} permissions`, template: tpl, actions: [this.actRemoveLocal, ACTION_CANCEL, ACTION_OK] })
      .subscribe((r) => {
        if (r === this.actRemoveLocal.result) {
          this.users.updatePermission(user, null).subscribe();
        } else if (r) {
          this.users.updatePermission(user, this.modPerm).subscribe();
        }
      });
  }

  /* template */ getAvailablePermissionsFor(user: UserInfo): Permission[] {
    if (!user) {
      return [];
    }

    // only permissions HIGHER than the current global permission are avilable.
    const glob = this.getGlobalPermissionLevel(user);
    const allPerms = [Permission.WRITE, Permission.ADMIN];

    if (!glob) return allPerms;
    if (glob === Permission.READ) return allPerms;
    if (glob === Permission.WRITE) return allPerms.slice(1);

    return [];
  }
}
