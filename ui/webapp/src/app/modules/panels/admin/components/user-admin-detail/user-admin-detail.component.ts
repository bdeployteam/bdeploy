import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { PermissionColumnsService } from 'src/app/modules/core/services/permission-columns.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { UserAvatarComponent } from '../../../../core/components/user-avatar/user-avatar.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-user-admin-detail',
  templateUrl: './user-admin-detail.component.html',
  styleUrls: ['./user-admin-detail.component.css'],
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    UserAvatarComponent,
    MatTooltip,
    BdDataTableComponent,
    BdButtonComponent,
    BdPanelButtonComponent,
    AsyncPipe,
  ],
})
export class UserAdminDetailComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly authAdmin = inject(AuthAdminService);
  private readonly auth = inject(AuthenticationService);
  private readonly permissionColumnsService = inject(PermissionColumnsService);

  private readonly colDeletePerm: BdDataColumn<ScopedPermission, string> = {
    id: 'delete',
    name: 'Delete',
    data: () => `Delete Permission`,
    action: (r) => this.onRemovePermission(r),
    icon: () => 'delete',
    width: '40px',
  };

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected user$ = new BehaviorSubject<UserInfo>(null);
  protected readonly permColumns: BdDataColumn<ScopedPermission, unknown>[] = [
    ...this.permissionColumnsService.defaultPermissionColumns,
    this.colDeletePerm,
  ];
  protected isCurrentUser: boolean;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.authAdmin.users$]).subscribe(([route, users]) => {
      if (!users || !route?.params?.['user']) {
        this.user$.next(null);
        return;
      }
      const user = users.find((u) => u.name === route.params['user']);
      this.user$.next(user);
      this.isCurrentUser = user.name === this.auth.getCurrentUsername();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private onRemovePermission(perm: ScopedPermission): void {
    const user = this.user$.value;

    if (this.isCurrentUser && perm.scope === null) {
      // refuse to remove global permissions from current user
      this.dialog
        .info('Cannot remove global permission', 'You cannot remove global permissions from yourself.', 'warning')
        .subscribe();
      return;
    }

    this.loading$.next(true);
    user.permissions.splice(user.permissions.indexOf(perm), 1);
    this.authAdmin
      .updateUser(user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* template */ onSetInactive(userInfo: UserInfo, newValue: boolean): void {
    this.loading$.next(true);
    userInfo.inactive = newValue;
    this.authAdmin
      .updateUser(userInfo)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  protected onDelete(userInfo: UserInfo): void {
    this.dialog.confirm('Delete User', `Are you sure you want to delete user ${userInfo.name}?`).subscribe((r) => {
      if (!r) {
        return;
      }

      this.loading$.next(true);
      this.authAdmin
        .deleteUser(userInfo.name)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe(() => {
          this.areas.closePanel();
        });
    });
  }
}
