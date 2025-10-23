import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, map, Observable, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission, UserGroupInfo, UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { PermissionColumnsService } from 'src/app/modules/core/services/permission-columns.service';
import { UsersColumnsService } from 'src/app/modules/core/services/users-columns.service';
import { ALL_USERS_GROUP_ID } from 'src/app/modules/core/utils/user-group.utils';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { AddUserToGroupComponent } from '../add-user-to-group/add-user-to-group.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-user-group-admin-detail',
  templateUrl: './user-group-admin-detail.component.html',
  styleUrls: ['./user-group-admin-detail.component.css'],
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MatIcon,
    MatTooltip,
    AddUserToGroupComponent,
    BdDataTableComponent,
    MatDivider,
    BdButtonComponent,
    BdPanelButtonComponent,
    AsyncPipe,
  ],
})
export class UserGroupAdminDetailComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly authAdmin = inject(AuthAdminService);
  private readonly usersColumnsService = inject(UsersColumnsService);
  private readonly permissionColumnsService = inject(PermissionColumnsService);

  private readonly colDeletePerm: BdDataColumn<ScopedPermission, string> = {
    id: 'deletePermission',
    name: 'Delete',
    data: () => `Delete Permission`,
    action: (r) => this.onRemovePermission(r),
    icon: () => 'delete',
    width: '40px',
  };

  private readonly colDeleteUser: BdDataColumn<UserInfo, string> = {
    id: 'removeUser',
    name: 'Remove',
    data: () => `Remove User`,
    action: (r) => this.onRemoveUser(r),
    icon: () => 'delete',
    width: '40px',
  };

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected group$ = new BehaviorSubject<UserGroupInfo>(null);
  protected readonly permColumns: BdDataColumn<ScopedPermission, unknown>[] = [
    ...this.permissionColumnsService.defaultPermissionColumns,
    this.colDeletePerm,
  ];
  protected readonly userColumns: BdDataColumn<UserInfo, unknown>[] = [
    ...this.usersColumnsService.userGroupAdminDetailsColumns,
    this.colDeleteUser,
  ];
  protected users: UserInfo[] = [];
  protected suggestedUsers: UserInfo[] = [];

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  private subscription: Subscription;

  protected isNotAllUsersGroup$: Observable<boolean> = this.group$.pipe(map((g) => g?.id !== ALL_USERS_GROUP_ID));

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.authAdmin.userGroups$]).subscribe(
      ([route, groups]) => {
        if (!groups || !route?.params?.['group']) {
          this.group$.next(null);
          return;
        }
        const group = groups.find((g) => g.id === route.params['group']);
        this.group$.next(group);
      }
    );
    this.subscription.add(
      combineLatest([this.authAdmin.users$, this.group$]).subscribe(([users, group]) => {
        if (!users || !group) {
          this.users = [];
          this.suggestedUsers = [];
        } else {
          this.users = users.filter((u) => u.groups.includes(group.id));
          this.suggestedUsers = users.filter((u) => !u.groups.includes(group.id));
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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

  protected addUser(info: UserInfo): void {
    const group = this.group$.value.id;
    const user = info.name;
    this.loading$.next(true);
    this.authAdmin
      .addUserToGroup(group, user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  protected onSetInactive(userGroupInfo: UserGroupInfo, newValue: boolean): void {
    this.loading$.next(true);
    userGroupInfo.inactive = newValue;
    this.authAdmin
      .updateUserGroup(userGroupInfo)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  protected onDelete(group: UserGroupInfo): void {
    this.dialog
      .confirm('Delete User Group', `Are you sure you want to delete user group ${group.name}?`)
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
