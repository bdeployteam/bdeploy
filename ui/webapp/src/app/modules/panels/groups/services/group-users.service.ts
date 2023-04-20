import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  InstanceGroupConfiguration,
  ObjectChangeType,
  Permission,
  UserGroupInfo,
  UserGroupPermissionUpdateDto,
  UserInfo,
  UserPermissionUpdateDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupUsersService {
  public loadingUsers$ = new BehaviorSubject<boolean>(false);
  public loadingUserGroups$ = new BehaviorSubject<boolean>(false);
  public loading$ = combineLatest({
    u: this.loadingUsers$,
    g: this.loadingUserGroups$,
  }).pipe(map(({ u, g }) => u || g));
  public users$ = new BehaviorSubject<UserInfo[]>(null);
  public userGroups$ = new BehaviorSubject<UserGroupInfo[]>(null);

  private group: InstanceGroupConfiguration;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private groups: GroupsService
  ) {
    this.groups.current$.subscribe((g) => {
      this.group = g;
      this.loadUsers();
      this.loadUserGroups();
    });
    this.changes.subscribe(ObjectChangeType.USER, EMPTY_SCOPE, () => {
      this.loadUsers();
    });
    this.changes.subscribe(ObjectChangeType.USER_GROUP, EMPTY_SCOPE, () => {
      this.loadUserGroups();
    });
  }

  private loadUsers() {
    if (!this.group) {
      return;
    }
    this.loadingUsers$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath(this.group.name)}/users`)
      .pipe(
        measure('Load Users'),
        finalize(() => this.loadingUsers$.next(false))
      )
      .subscribe((users) => {
        this.users$.next(users);
      });
  }

  private loadUserGroups() {
    if (!this.group) {
      return;
    }
    this.loadingUserGroups$.next(true);
    this.http
      .get<UserGroupInfo[]>(`${this.apiPath(this.group.name)}/user-groups`)
      .pipe(
        measure('Load User Groups'),
        finalize(() => this.loadingUserGroups$.next(false))
      )
      .subscribe((groups) => {
        this.userGroups$.next(groups);
      });
  }

  public updatePermission(user: UserInfo, modPerm: Permission) {
    const upd: UserPermissionUpdateDto = {
      user: user.name,
      permission: modPerm,
    };
    return this.http
      .post(`${this.apiPath(this.group.name)}/user-permissions`, [upd])
      .pipe(measure('Update user permission'));
  }

  public updateUserGroupPermission(group: UserGroupInfo, modPerm: Permission) {
    const upd: UserGroupPermissionUpdateDto = {
      group: group.id,
      permission: modPerm,
    };
    return this.http
      .post(`${this.apiPath(this.group.name)}/user-group-permissions`, [upd])
      .pipe(measure('Update user group permission'));
  }
}
