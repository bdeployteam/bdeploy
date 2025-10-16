import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize, first, skipWhile } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { PermissionColumnsService } from 'src/app/modules/core/services/permission-columns.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { UserAvatarComponent } from '../../../../core/components/user-avatar/user-avatar.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';

interface UserGroupPermission extends ScopedPermission {
  // XXX: UserGroupPermission list also contains the default permissions
  // so the root type has to be convertible to the child type
  userGroupName?: string;
  userGroupId?: string;
}

const colUserGroupName: BdDataColumn<UserGroupPermission, string> = {
  id: 'userGroup',
  name: 'Group',
  data: (r) => r.userGroupName,
};

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    UserAvatarComponent,
    MatTooltip,
    BdExpandButtonComponent,
    BdDataTableComponent,
    BdPanelButtonComponent,
    BdButtonComponent,
  ],
})
export class SettingsComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly areas = inject(NavAreasService);
  private readonly permissionColumnsService = inject(PermissionColumnsService);
  private readonly authService = inject(AuthenticationService);

  private readonly colDeletePermission: BdDataColumn<UserGroupPermission, string> = {
    id: 'deletePermission',
    name: 'Delete',
    data: () => `Delete Permission`,
    action: (r) => this.onDeletePermission(r),
    icon: () => 'delete',
    width: '40px',
  };

  private readonly colLeaveGroup: BdDataColumn<UserGroupPermission, string> = {
    id: 'leaveGroupButton',
    name: 'Leave',
    data: () => `Leave Group`,
    action: (r) => this.onLeaveGroup(r),
    icon: () => 'delete',
    width: '40px',
  };

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected user: UserInfo;
  protected userGroupPermissions: UserGroupPermission[];
  protected readonly permColumns: BdDataColumn<ScopedPermission, unknown>[] = [
    ...this.permissionColumnsService.defaultPermissionColumns,
    this.colDeletePermission,
  ];
  protected readonly userGroupPermColumns: BdDataColumn<UserGroupPermission, unknown>[] = [
    colUserGroupName,
    ...this.permissionColumnsService.defaultPermissionColumns,
    this.colLeaveGroup,
  ];

  ngOnInit(): void {
    this.authService
      .getUserInfo()
      .pipe(
        skipWhile((u) => !u),
        first(),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((r) => {
        this.user = r;
      });

    this.authService
      .getUserProfileInfo()
      .subscribe(
        (r) =>
          (this.userGroupPermissions = r.userGroups.flatMap((userGroup) =>
            userGroup.permissions.map((p) => ({ userGroupName: userGroup.name, userGroupId: userGroup.id, ...p }))
          ))
      );
  }

  private onDeletePermission(perm: ScopedPermission): void {
    this.dialog
      .confirm(
        'Leave group',
        `Are you REALLY sure that you want to remove your own ${perm.permission} permission in the ${
          perm.scope ? 'scope ' + perm.scope : 'global scope'
        }? You might not be able to regain this permission on your own.`
      )
      .subscribe((result) => {
        if (result) {
          this.loading$.next(true);
          const index = this.user.permissions.indexOf(perm);
          const demotedUser = structuredClone(this.user);
          demotedUser.permissions = demotedUser.permissions.filter((_, i) => i !== index);
          this.authService
            .updateCurrentUser(demotedUser)
            .pipe(finalize(() => this.loading$.next(false)))
            .subscribe(() => (this.user = demotedUser));
        }
      });
  }

  private onLeaveGroup(perm: ScopedPermission): void {
    const index = this.userGroupPermissions.indexOf(perm);
    const groupPerm = this.userGroupPermissions[index];
    this.dialog
      .confirm(
        'Leave group',
        `Are you REALLY sure that you want to leave the group ${groupPerm.userGroupName}? You might not be able to rejoin on your own.`
      )
      .subscribe((result) => {
        if (result) {
          this.loading$.next(true);
          this.authService
            .removeCurrentUserFromGroup(groupPerm.userGroupId)
            .pipe(finalize(() => this.loading$.next(false)))
            .subscribe(() => (this.userGroupPermissions = this.userGroupPermissions.filter((_, i) => i !== index)));
        }
      });
  }

  protected onDeactivateSelf() {
    this.dialog
      .confirm(
        'Deactivate your own user',
        `Are you REALLY sure that you want to deactivate your own user? You will be logged out and cannot log in again.`
      )
      .subscribe((result) => {
        if (result) {
          this.loading$.next(true);
          const inactivatedUser = structuredClone(this.user);
          inactivatedUser.inactive = true;
          this.authService
            .updateCurrentUser(inactivatedUser)
            .pipe(finalize(() => this.loading$.next(false)))
            .subscribe(() => {
              this.areas.navigateBoth(['/login'], null);
              this.authService.logout();
            });
        }
      });
  }

  protected onDeleteSelf() {
    this.dialog
      .confirm(
        'Delete your own user',
        `Are you REALLY sure that you want to delete your own user? You will be logged out and cannot log in again.`
      )
      .subscribe((result) => {
        if (result) {
          this.loading$.next(true);
          this.authService
            .deleteCurrentUser()
            .pipe(finalize(() => this.loading$.next(false)))
            .subscribe(() => {
              this.areas.navigateBoth(['/login'], null);
              this.authService.logout();
            });
        }
      });
  }

  protected logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']).then(() => {
        console.log('user logged out');
      });
    });
  }
}
