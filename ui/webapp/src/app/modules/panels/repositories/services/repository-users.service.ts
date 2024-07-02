import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  ObjectChangeType,
  Permission,
  SoftwareRepositoryConfiguration,
  UserGroupInfo,
  UserGroupPermissionUpdateDto,
  UserInfo,
  UserPermissionUpdateDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryUsersService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly repos = inject(RepositoriesService);

  public loadingUsers$ = new BehaviorSubject<boolean>(false);
  public loadingUserGroups$ = new BehaviorSubject<boolean>(false);
  public loading$ = combineLatest({
    u: this.loadingUsers$,
    g: this.loadingUserGroups$,
  }).pipe(map(({ u, g }) => u || g));
  public users$ = new BehaviorSubject<UserInfo[]>([]);
  public userGroups$ = new BehaviorSubject<UserGroupInfo[]>([]);

  private repo: SoftwareRepositoryConfiguration;
  private readonly apiPath = (g) => `${this.cfg.config.api}/softwarerepository/${g}`;

  constructor() {
    this.repos.current$.subscribe((r) => {
      this.repo = r;
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
    if (!this.repo) {
      return;
    }

    this.loadingUsers$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath(this.repo.name)}/users`)
      .pipe(
        measure('Load Users'),
        finalize(() => this.loadingUsers$.next(false)),
      )
      .subscribe((res) => {
        this.users$.next(res);
      });
  }

  private loadUserGroups() {
    if (!this.repo) {
      return;
    }

    this.loadingUserGroups$.next(true);
    this.http
      .get<UserGroupInfo[]>(`${this.apiPath(this.repo.name)}/user-groups`)
      .pipe(
        measure('Load User Groups'),
        finalize(() => this.loadingUserGroups$.next(false)),
      )
      .subscribe((res) => {
        this.userGroups$.next(res);
      });
  }

  public updateUserPermission(user: UserInfo, modPerm: Permission) {
    const upd: UserPermissionUpdateDto = {
      user: user.name,
      permission: modPerm,
    };
    return this.http
      .post(`${this.apiPath(this.repo.name)}/user-permissions`, [upd])
      .pipe(measure('Update user permission'));
  }

  public updateUserGroupPermission(group: UserGroupInfo, modPerm: Permission) {
    const upd: UserGroupPermissionUpdateDto = {
      group: group.id,
      permission: modPerm,
    };
    return this.http
      .post(`${this.apiPath(this.repo.name)}/user-group-permissions`, [upd])
      .pipe(measure('Update user group permission'));
  }
}
