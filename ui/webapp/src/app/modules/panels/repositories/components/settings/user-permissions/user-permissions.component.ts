import { Component, inject, Input, TemplateRef, ViewChild } from '@angular/core';
import { combineLatest, map, Observable } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { Permission, UserInfo } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
  BdDialogMessageAction
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import {
  getGlobalOrLocalPermission,
  getInheritedPermissionHint,
  getInheritedPermissions
} from 'src/app/modules/core/utils/permission.utils';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import {
  BdDataPermissionLevelCellComponent
} from '../../../../../core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { UsersColumnsService } from '../../../../../core/services/users-columns.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';
import { BdFormSelectComponent } from '../../../../../core/components/bd-form-select/bd-form-select.component';
import { FormsModule } from '@angular/forms';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-software-repository-user-permissions',
    templateUrl: './user-permissions.component.html',
    imports: [BdFormSelectComponent, FormsModule, BdDataTableComponent, AsyncPipe]
})
export class UserPermissionsComponent {
  public readonly repos = inject(RepositoriesService);
  public readonly users = inject(RepositoryUsersService);
  public readonly userCols = inject(UsersColumnsService);

  private readonly colInheritedPerm: BdDataColumn<UserInfo> = {
    id: 'inherited',
    name: 'Inherited Perm.',
    data: (r) => this.getInheritedPermissionLevel(r),
    width: '100px',
    tooltip: (r) => this.getInheritedPermissionTooltip(r),
    component: BdDataPermissionLevelCellComponent
  };

  private readonly colGlobalPerm: BdDataColumn<UserInfo> = {
    id: 'global',
    name: 'Global Perm.',
    data: (r) => this.getGlobalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent
  };

  private readonly colLocalPerm: BdDataColumn<UserInfo> = {
    id: 'local',
    name: 'Local Perm.',
    data: (r) => this.getLocalPermissionLevel(r),
    width: '100px',
    component: BdDataPermissionLevelCellComponent
  };

  private readonly colModPerm: BdDataColumn<UserInfo> = {
    id: 'modify',
    name: 'Modify',
    data: (r) => `Modify permissions for ${r.name}`,
    action: (r) => this.doModify(this.modDialog, r),
    actionDisabled: (r) => this.getAvailablePermissionsFor(r).length === 0,
    icon: (r) => (!this.getLocalPermissionLevel(r) && !this.getGlobalPermissionLevel(r) ? 'add' : 'edit'),
    width: '40px'
  };

  private readonly actRemoveLocal: BdDialogMessageAction<unknown> = {
    name: 'Remove',
    result: 'REMOVE',
    confirm: false,
    disabled: () => !this.getLocalPermissionLevel(this.modUser)
  };

  protected readonly columns: BdDataColumn<UserInfo>[] = [
    ...this.userCols.defaultUsersColumns,
    this.colInheritedPerm,
    this.colGlobalPerm,
    this.colLocalPerm,
    this.colModPerm
  ];

  private readonly groupNames = ['CLIENT', 'READ', 'WRITE', 'ADMIN'];

  protected grouping$: Observable<BdDataGrouping<UserInfo>[]> = combineLatest([
    this.repos.current$,
    this.users.userGroups$
  ]).pipe(
    map(([repo, userGroups]) => {
      if (!repo || !userGroups) {
        return [];
      }
      const scope = repo.name;
      return [
        {
          definition: {
            group: (r) => {
              const permissions = [...r.permissions, ...getInheritedPermissions(r, userGroups)];
              return getGlobalOrLocalPermission(permissions, scope);
            },
            name: 'Permission',
            sort: (a, b) => this.groupNames.indexOf(b) - this.groupNames.indexOf(a)
          },
          selected: []
        }
      ];
    })
  );

  protected modPerm: Permission;
  protected modUser: UserInfo;
  protected availablePermissions: Permission[];

  @Input() dialog: BdDialogComponent;
  @ViewChild('modDialog') private readonly modDialog: TemplateRef<unknown>;

  private getInheritedPermissionTooltip(user: UserInfo): string {
    const scope = this.repos.current$.value.name;
    const userGroups = this.users.userGroups$.value;
    return getInheritedPermissionHint(user, userGroups, scope);
  }

  private getInheritedPermissionLevel(user: UserInfo): Permission {
    const scope = this.repos.current$.value.name;
    const userGroups = this.users.userGroups$.value;
    const inheritedPermissions = getInheritedPermissions(user, userGroups);
    return getGlobalOrLocalPermission(inheritedPermissions, scope);
  }

  private getLocalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((p) => p.scope === this.repos.current$.value.name)?.permission;
  }

  private getGlobalPermissionLevel(user: UserInfo): Permission {
    return user.permissions.find((c) => !c.scope)?.permission;
  }

  private doModify(tpl: TemplateRef<unknown>, user: UserInfo) {
    this.modUser = user;
    this.modPerm = this.getLocalPermissionLevel(user);
    this.dialog
      .message({
        header: `Modify ${user.name} permissions`,
        template: tpl,
        actions: [this.actRemoveLocal, ACTION_CANCEL, ACTION_OK]
      })
      .subscribe((r) => {
        if (r === this.actRemoveLocal.result) {
          this.users.updateUserPermission(user, null).subscribe();
        } else if (r) {
          this.users.updateUserPermission(user, this.modPerm).subscribe();
        }
      });
    this.availablePermissions = this.getAvailablePermissionsFor(user);
  }

  private getAvailablePermissionsFor(user: UserInfo): Permission[] {
    if (!user) {
      return [];
    }

    // only permissions HIGHER than the current global permission are avilable.
    const glob = this.getGlobalPermissionLevel(user);
    const allPerms = [Permission.READ, Permission.WRITE, Permission.ADMIN];

    if (!glob) return allPerms;
    if (glob === Permission.READ) return allPerms.slice(1);
    if (glob === Permission.WRITE) return allPerms.slice(2);

    return [];
  }
}
