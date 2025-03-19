import { Component, OnInit, inject } from '@angular/core';
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

interface UserGroupPermission extends ScopedPermission {
  // XXX: UserGroupPermission list also contains the default permissions
  // so the root type has to be convertible to the child type
  userGroup?: string;
}

const colUserGroupName: BdDataColumn<UserGroupPermission, string> = {
  id: 'userGroup',
  name: 'User Group',
  data: (r) => r.userGroup,
};

@Component({
    selector: 'app-settings',
    templateUrl: './settings.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, UserAvatarComponent, MatTooltip, BdExpandButtonComponent, BdDataTableComponent, BdPanelButtonComponent, BdButtonComponent]
})
export class SettingsComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly permissionColumnsService = inject(PermissionColumnsService);
  private readonly authService = inject(AuthenticationService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected user: UserInfo;
  protected userGroupPermissions: UserGroupPermission[];
  protected readonly permColumns: BdDataColumn<ScopedPermission, unknown>[] = [
    ...this.permissionColumnsService.defaultPermissionColumns,
  ];
  protected readonly userGroupPermColumns: BdDataColumn<UserGroupPermission, unknown>[] = [
    colUserGroupName,
    ...this.permissionColumnsService.defaultPermissionColumns,
  ];

  ngOnInit(): void {
    this.authService
      .getUserInfo()
      .pipe(
        skipWhile((u) => !u),
        first(),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe((r) => {
        this.user = r;
      });

    this.authService
      .getUserProfileInfo()
      .subscribe(
        (r) =>
          (this.userGroupPermissions = r.userGroups.flatMap((userGroup) =>
            userGroup.permissions.map((p) => ({ userGroup: userGroup.name, ...p })),
          )),
      );
  }

  protected logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']).then(() => {
        console.log('user logged out');
      });
    });
  }
}
