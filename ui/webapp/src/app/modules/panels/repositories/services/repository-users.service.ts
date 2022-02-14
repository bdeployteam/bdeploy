import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  ObjectChangeType,
  Permission,
  SoftwareRepositoryConfiguration,
  UserInfo,
  UserPermissionUpdateDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryUsersService {
  public loading$ = new BehaviorSubject<boolean>(false);
  public users$ = new BehaviorSubject<UserInfo[]>(null);

  private repo: SoftwareRepositoryConfiguration;
  private apiPath = (g) => `${this.cfg.config.api}/softwarerepository/${g}`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private repos: RepositoriesService
  ) {
    this.repos.current$.subscribe((r) => {
      this.repo = r;
      this.loadUsers();
    });
    this.changes.subscribe(ObjectChangeType.USER, EMPTY_SCOPE, () => {
      this.loadUsers();
    });
  }

  public loadUsers() {
    if (!this.repo) {
      return;
    }

    this.loading$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath(this.repo.name)}/users`)
      .pipe(
        measure('Load Users'),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((res) => {
        this.users$.next(res);
      });
  }

  public updatePermission(user: UserInfo, modPerm: Permission) {
    const upd: UserPermissionUpdateDto = {
      user: user.name,
      permission: modPerm,
    };
    return this.http
      .post(`${this.apiPath(this.repo.name)}/permissions`, [upd])
      .pipe(measure('Update user permission'));
  }
}
