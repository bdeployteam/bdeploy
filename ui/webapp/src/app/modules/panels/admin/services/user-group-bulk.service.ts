import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, finalize, map, switchMap, take, tap } from 'rxjs';
import {
  BulkOperationResultDto,
  ScopedPermission,
  UserGroupBulkAssignPermissionDto,
  UserGroupBulkRemovePermissionDto,
  UserGroupInfo,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Injectable({
  providedIn: 'root',
})
export class UserGroupBulkService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private auth = inject(AuthAdminService);
  private areas = inject(NavAreasService);

  public selection$ = new BehaviorSubject<UserGroupInfo[]>([]);
  public frozen$ = new BehaviorSubject<boolean>(false);

  private bulkApiPath = () => `${this.cfg.config.api}/auth/admin/user-group-bulk`;

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => this.selection$.next([]));

    // find matching selected instances if possible once instances change.
    this.auth.userGroups$.subscribe((groups) => {
      const newSelection: UserGroupInfo[] = [];
      this.selection$.value.forEach((g) => {
        const found = groups.find((group) => group.id === g.id);
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
      map((g) => g.map((group) => group.id)),
      switchMap((ids) => {
        return this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/delete`, ids);
      }),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public setInactive(inactive: boolean): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((g) => g.map((group) => group.id)),
      switchMap((ids) => {
        return this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/inactive/${inactive}`, ids);
      }),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public assignPermission(scopedPermission: ScopedPermission): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((g) => g.map((group) => group.id)),
      map((groupIds) => ({ scopedPermission, groupIds })),
      switchMap((data: UserGroupBulkAssignPermissionDto) => {
        return this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/assign-permission`, data);
      }),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  public removePermission(scope: string): Observable<BulkOperationResultDto> {
    this.frozen$.next(true);
    return this.selection$.pipe(
      take(1),
      map((g) => g.map((group) => group.id)),
      map((groupIds) => ({ scope, groupIds })),
      switchMap((data: UserGroupBulkRemovePermissionDto) => {
        return this.http.post<BulkOperationResultDto>(`${this.bulkApiPath()}/remove-permission`, data);
      }),
      tap((r) => this.logResult(r)),
      finalize(() => this.frozen$.next(false)),
    );
  }

  private logResult(result: BulkOperationResultDto): void {
    if (result && result?.results?.length) {
      for (const r of result.results) {
        console.log(`${r.target}: ${r.type}: ${r.message}`);
      }
    }
  }
}
