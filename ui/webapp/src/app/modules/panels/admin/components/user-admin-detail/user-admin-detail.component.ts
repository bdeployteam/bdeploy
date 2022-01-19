import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

const COL_SCOPE: BdDataColumn<ScopedPermission> = {
  id: 'scope',
  name: 'Scope',
  data: (r) => (r.scope ? r.scope : 'Global'),
  classes: (r) => (r.scope ? [] : ['bd-text-secondary']),
};

const COL_PERMISSION: BdDataColumn<ScopedPermission> = {
  id: 'permission',
  name: 'Permission',
  data: (r) => r.permission,
  component: BdDataPermissionLevelCellComponent,
};

@Component({
  selector: 'app-user-admin-detail',
  templateUrl: './user-admin-detail.component.html',
  styleUrls: ['./user-admin-detail.component.css'],
})
export class UserAdminDetailComponent implements OnInit, OnDestroy {
  private readonly colDeletePerm: BdDataColumn<ScopedPermission> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => `Delete Permission`,
    action: (r) => this.onRemovePermission(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ user$ = new BehaviorSubject<UserInfo>(null);
  /* template */ permColumns: BdDataColumn<ScopedPermission>[] = [COL_SCOPE, COL_PERMISSION, this.colDeletePerm];
  /* template */ isCurrentUser: boolean;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private authAdmin: AuthAdminService,
    private auth: AuthenticationService,
    groups: GroupsService,
    private router: Router
  ) {
    this.subscription = combineLatest([areas.panelRoute$, authAdmin.users$]).subscribe(([route, users]) => {
      if (!users || !route?.params || !route.params['user']) {
        this.user$.next(null);
        return;
      }
      const user = users.find((u) => u.name === route.params['user']);
      this.user$.next(user);
      this.isCurrentUser = user.name === this.auth.getUsername();
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private onRemovePermission(perm: ScopedPermission): void {
    const user = this.user$.value;

    if (this.isCurrentUser && perm.scope === null) {
      // refuse to remove global permissions from current user
      this.dialog.info('Cannot remove global permission', 'You cannot remove global permissions from yourself.', 'warning').subscribe();
      return;
    }

    this.loading$.next(true);
    user.permissions.splice(user.permissions.indexOf(perm), 1);
    this.authAdmin
      .updateUser(user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* template */ onAssignPermission(user: UserInfo): void {
    this.router.navigate([{ outlets: { panel: ['panels', 'admin', 'user-detail', user.name, 'permission'] } }]);
  }

  /* template */ onEdit(user: UserInfo): void {
    this.router.navigate([{ outlets: { panel: ['panels', 'admin', 'user-detail', user.name, 'edit'] } }]);
  }

  // TODO: Remove if it's unused
  /* template */ hasAnyPermission(scope: string): boolean {
    return !!this.user$.value?.permissions.find((p) => p.scope === scope);
  }

  /* tepmlate */ onSetInactive(userInfo: UserInfo, newValue: boolean): void {
    this.loading$.next(true);
    userInfo.inactive = newValue;
    this.authAdmin
      .updateUser(userInfo)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* template */ onDelete(userInfo: UserInfo): void {
    this.dialog.confirm('Delete User', `Are you sure you want to delete user ${userInfo.name}?`).subscribe((r) => {
      if (!r) {
        return;
      }

      this.loading$.next(true);
      this.authAdmin
        .deleteUser(userInfo.name)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe((_) => {
          this.areas.closePanel();
        });
    });
  }
}
