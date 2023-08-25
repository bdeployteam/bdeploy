import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, finalize } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  ScopedPermission,
  UserGroupInfo,
  UserInfo,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { PermissionColumnsService } from 'src/app/modules/core/services/permission-columns.service';
import { UsersColumnsService } from 'src/app/modules/core/services/users-columns.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  selector: 'app-user-group-admin-detail',
  templateUrl: './user-group-admin-detail.component.html',
  styleUrls: ['./user-group-admin-detail.component.css'],
})
export class UserGroupAdminDetailComponent implements OnDestroy {
  private readonly colDeletePerm: BdDataColumn<ScopedPermission> = {
    id: 'deletePermission',
    name: 'Delete',
    data: () => `Delete Permission`,
    action: (r) => this.onRemovePermission(r),
    icon: () => 'delete',
    width: '40px',
  };

  private readonly colDeleteUser: BdDataColumn<UserInfo> = {
    id: 'removeUser',
    name: 'Remove',
    data: () => `Remove User`,
    action: (r) => this.onRemoveUser(r),
    icon: () => 'delete',
    width: '40px',
  };

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ group$ = new BehaviorSubject<UserGroupInfo>(null);
  /* template */ permColumns: BdDataColumn<ScopedPermission>[] = [
    ...this.permissionColumnsService.defaultPermissionColumns,
    this.colDeletePerm,
  ];
  /* template */ userColumns: BdDataColumn<UserInfo>[] = [
    ...this.usersColumnsService.userGroupAdminDetailsColumns,
    this.colDeleteUser,
  ];
  /* template */ users: UserInfo[] = [];
  /* template */ suggestedUsers: UserInfo[] = [];

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private authAdmin: AuthAdminService,
    private usersColumnsService: UsersColumnsService,
    private permissionColumnsService: PermissionColumnsService
  ) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      authAdmin.userGroups$,
    ]).subscribe(([route, groups]) => {
      if (!groups || !route?.params || !route.params['group']) {
        this.group$.next(null);
        return;
      }
      const group = groups.find((g) => g.id === route.params['group']);
      this.group$.next(group);
    });
    this.subscription.add(
      combineLatest([authAdmin.users$, this.group$]).subscribe(
        ([users, group]) => {
          if (!users || !group) {
            this.users = [];
            this.suggestedUsers = [];
          } else {
            this.users = users.filter((u) => u.groups.indexOf(group.id) >= 0);
            this.suggestedUsers = users.filter(
              (u) => u.groups.indexOf(group.id) == -1
            );
          }
        }
      )
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private onRemovePermission(perm: ScopedPermission): void {
    const group = this.group$.value;
    this.loading$.next(true);
    group.permissions.splice(group.permissions.indexOf(perm), 1);
    this.authAdmin
      .updateUserGroup(group)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  private onRemoveUser(info: UserInfo): void {
    const group = this.group$.value.id;
    const user = info.name;
    this.loading$.next(true);
    this.authAdmin
      .removeUserFromGroup(group, user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* template */ addUser(info: UserInfo): void {
    const group = this.group$.value.id;
    const user = info.name;
    this.loading$.next(true);
    this.authAdmin
      .addUserToGroup(group, user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* tepmlate */ onSetInactive(
    userGroupInfo: UserGroupInfo,
    newValue: boolean
  ): void {
    this.loading$.next(true);
    userGroupInfo.inactive = newValue;
    this.authAdmin
      .updateUserGroup(userGroupInfo)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  /* template */ onDelete(group: UserGroupInfo): void {
    this.dialog
      .confirm(
        'Delete User Group',
        `Are you sure you want to delete user group ${group.name}?`
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.loading$.next(true);
        this.authAdmin
          .deleteUserGroup(group.id)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe(() => {
            this.areas.closePanel();
          });
      });
  }
}
