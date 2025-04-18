import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, finalize, map, switchMap, take, tap } from 'rxjs';
import {
  BulkOperationResultDto,
  ScopedPermission,
  UserBulkAssignPermissionDto,
  UserBulkRemovePermissionDto,
  UserInfo,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Injectable({
  providedIn: 'root',
})
export class UserBulkService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthAdminService);
  private readonly areas = inject(NavAreasService);

  public selection$ = new BehaviorSubject<UserInfo[]>([]);
  public frozen$ = new BehaviorSubject<boolean>(false);

  private readonly bulkApiPath = () => `${this.cfg.config.api}/auth/admin/user-bulk`;

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => this.selection$.next([]));

    // find matching selected users if possible once users change.
    this.auth.users$.subscribe((users) => {
      const newSelection: UserInfo[] = [];
      this.selection$.value.forEach((u) => {
        const found = users.find((user) => user.name === u.name);
        if (found) {
          newSelection.push(found);
        }
      });
      this.selection$.next(newSelection);
    });
  }

  public delete(): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((users) => users.map((user) => user.name)),
      switchMap((names) => this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/delete`, names)),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public setInactive(inactive: boolean): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((users) => users.map((user) => user.name)),
      switchMap((names) => this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/inactive/${inactive}`, names)),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public assignPermission(scopedPermission: ScopedPermission): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((users) => users.map((user) => user.name)),
      map((userNames) => ({ scopedPermission, userNames })),
      switchMap((data: UserBulkAssignPermissionDto) =>
        this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/assign-permission`, data),
      ),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public removePermission(scope: string): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((users) => users.map((user) => user.name)),
      map((userNames) => ({ scope, userNames })),
      switchMap((data: UserBulkRemovePermissionDto) =>
        this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/remove-permission`, data),
      ),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public addToGroup(groupId: string): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((users) => users.map((user) => user.name)),
      switchMap((names) =>
        this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/add-to-group/${groupId}`, names),
      ),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  private logResult(result: BulkOperationResultDto): void {
    if (result?.results?.length) {
      for (const r of result.results) {
        console.log(`${r.target}: ${r.type}: ${r.message}`);
      }
    }
  }
}
