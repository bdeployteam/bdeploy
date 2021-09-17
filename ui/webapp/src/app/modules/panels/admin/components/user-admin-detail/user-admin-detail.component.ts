import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { Permission, ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
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
  /* template */ scopes$ = new BehaviorSubject<string[]>([null]);
  /* template */ labels$ = new BehaviorSubject<string[]>(['Global']);

  /* template */ assignScope: string;
  /* template */ assignPerm: Permission;

  /* template */ editUser: UserInfo;
  /* template */ passConfirm: string;

  /* template */ allPerms: Permission[] = Object.keys(Permission).map((k) => Permission[k]);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('assignTemplate') private assignTemplate: TemplateRef<any>;
  @ViewChild('editTemplate') private editTemplate: TemplateRef<any>;
  @ViewChild('editForm') private editForm: NgForm;

  private subscription: Subscription;

  constructor(private areas: NavAreasService, private authAdmin: AuthAdminService, private auth: AuthenticationService, groups: GroupsService) {
    this.subscription = combineLatest([areas.panelRoute$, authAdmin.users$]).subscribe(([route, users]) => {
      if (!users || !route?.params || !route.params['user']) {
        this.user$.next(null);
        return;
      }

      this.user$.next(users.find((u) => u.name === route.params['user']));
    });

    this.subscription.add(
      groups.groups$.subscribe((groups) => {
        if (!groups) {
          return;
        }

        const sortedNames = groups.map((g) => g.name).sort();

        this.scopes$.next([null, ...sortedNames]);
        this.labels$.next(['Global', ...sortedNames]);
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private onRemovePermission(perm: ScopedPermission): void {
    const user = this.user$.value;

    if (this.isCurrentUser(user) && perm.scope === null) {
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
    this.assignScope = null;
    this.assignPerm = null;
    this.dialog
      .message({ header: 'Assign Permission', template: this.assignTemplate, validation: () => !!this.assignPerm, actions: [ACTION_CANCEL, ACTION_OK] })
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.loading$.next(true);
        const existing = user.permissions.find((p) => p.scope === this.assignScope);
        if (!!existing) {
          existing.permission = this.assignPerm;
        } else {
          user.permissions.push({ scope: this.assignScope, permission: this.assignPerm });
        }

        this.authAdmin
          .updateUser(user)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe();
      });
  }

  /* template */ isCurrentUser(userInfo: UserInfo): boolean {
    return userInfo.name === this.auth.getUsername();
  }

  /* template */ onEdit(userInfo: UserInfo): void {
    this.editUser = { ...userInfo };
    this.passConfirm = null;
    this.dialog
      .message({
        header: 'Edit User',
        template: this.editTemplate,
        validation: () => !this.editForm || this.editForm.valid,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.loading$.next(true);
        this.authAdmin
          .updateUser(this.editUser)
          .pipe(
            switchMap((_) => {
              if (!!this.editUser.password?.length) {
                return this.authAdmin.updateLocalUserPassword(this.editUser.name, this.editUser.password);
              }

              return of(null);
            }),
            finalize(() => this.loading$.next(false))
          )
          .subscribe();
      });
  }

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

  public onDelete(userInfo: UserInfo): void {
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
