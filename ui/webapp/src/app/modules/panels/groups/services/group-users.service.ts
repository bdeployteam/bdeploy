import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceGroupConfiguration, ObjectChangeType, Permission, UserInfo, UserPermissionUpdateDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupUsersService {
  public loading$ = new BehaviorSubject<boolean>(false);
  public users$ = new BehaviorSubject<UserInfo[]>(null);

  private group: InstanceGroupConfiguration;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}`;

  constructor(private cfg: ConfigService, private http: HttpClient, private changes: ObjectChangesService, private groups: GroupsService) {
    this.groups.current$.subscribe((g) => {
      this.group = g;
      this.loadUsers();
    });
    this.changes.subscribe(ObjectChangeType.USER, EMPTY_SCOPE, (ch) => {
      this.loadUsers();
    });
  }

  public loadUsers() {
    if (!this.group) {
      return;
    }

    this.loading$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath(this.group.name)}/users`)
      .pipe(
        measure('Load Users'),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((res) => {
        this.users$.next(res);
      });
  }

  public updatePermission(user: UserInfo, modPerm: Permission) {
    const upd: UserPermissionUpdateDto = { user: user.name, permission: modPerm };
    return this.http.post(`${this.apiPath(this.group.name)}/permissions`, [upd]).pipe(measure('Update user permission'));
  }
}
