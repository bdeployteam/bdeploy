import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize, first, skipWhile } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { PermissionColumnsService } from 'src/app/modules/core/services/permission-columns.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

interface UserGroupPermission extends ScopedPermission {
  userGroup: string;
}

const colUserGroupName: BdDataColumn<UserGroupPermission> = {
  id: 'userGroup',
  name: 'User Group',
  data: (r) => r.userGroup,
};

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly permissionColumnsService = inject(PermissionColumnsService);
  protected authService = inject(AuthenticationService);
  protected settings = inject(SettingsService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected user: UserInfo;
  protected userGroupPermissions: UserGroupPermission[];
  protected permColumns: BdDataColumn<ScopedPermission>[] = [...this.permissionColumnsService.defaultPermissionColumns];
  protected userGroupPermColumns: BdDataColumn<ScopedPermission>[] = [
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
