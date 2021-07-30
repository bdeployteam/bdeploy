import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ApiAccessToken, CredentialsApi, Permission, UserChangePasswordDto, UserInfo } from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';
import { NavAreasService } from './nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private log: Logger = this.loggingService.getLogger('AuthenticationService');

  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(this.cookies.check('st') ? this.cookies.get('st') : null);

  public userInfoSubject: BehaviorSubject<UserInfo> = new BehaviorSubject(null);

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private cookies: CookieService,
    private areas: NavAreasService
  ) {}

  authenticate(username: string, password: string): Observable<any> {
    this.log.debug('authenticate("' + username + '", <...>)');

    return this.http
      .post(this.cfg.config.api + '/auth', { user: username, password: password } as CredentialsApi, {
        responseType: 'text',
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      })
      .pipe(
        tap(
          (result) => {
            this.tokenSubject.next(result);
            // this is required if the backend runs on a different server than the frontend (dev)
            // - don't use secure, as this will fail in the development case (HTTP server only).
            this.cookies.set('st', result, 365, '/', null, false, 'Strict');
          },
          (error) => {
            this.tokenSubject.next(null);
            this.cookies.delete('st', '/');
          }
        ),
        map(
          (result) => {
            this.log.debug('Fetching current user info...');
            this.http.get<UserInfo>(this.cfg.config.api + '/auth/user').pipe(tap((userInfo) => this.userInfoSubject.next(userInfo)));
          },
          (error) => {
            this.userInfoSubject.next(null);
          }
        )
      );
  }

  isAuthenticated(): boolean {
    return this.tokenSubject.value !== null;
  }

  getToken(): string {
    return this.tokenSubject.value;
  }

  getTokenSubject(): BehaviorSubject<string> {
    return this.tokenSubject;
  }

  private getTokenPayload(): ApiAccessToken {
    const payload: any = this.tokenSubject && this.tokenSubject.value ? JSON.parse(atob(this.tokenSubject.value)).p : null;
    return payload ? JSON.parse(atob(payload)) : null;
  }

  getUsername(): string {
    if (this.userInfoSubject.value) {
      return this.userInfoSubject.value.name;
    }
    const tokenPayload = this.getTokenPayload();
    return tokenPayload ? tokenPayload.it : null;
  }

  logout(): void {
    this.log.info('destroying session for user "' + this.getUsername() + '"');
    this.tokenSubject.next(null);
    this.cookies.delete('st', '/');
    window.location.reload();
  }

  getRecentInstanceGroups(): Observable<string[]> {
    return of([]);
  }

  isGlobalAdmin(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find((c) => c.scope === null && c.permission === Permission.ADMIN);
    }
    return false;
  }

  isGlobalWrite(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find((c) => c.scope === null && c.permission === Permission.WRITE);
    }
    return false;
  }

  isGlobalRead(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find((c) => c.scope === null && c.permission === Permission.READ);
    }
    return false;
  }

  isGlobalExclusiveReadClient(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      // if it has a CLIENT permission
      const clientPerm = tokenPayload.c.find((c) => c.scope === null && c.permission === Permission.CLIENT);
      // and *NO* other global permissions
      const nonClientPerm = tokenPayload.c.find((c) => c.scope === null && c.permission !== Permission.CLIENT);

      return !!clientPerm && !nonClientPerm;
    }
    return false;
  }

  isCurrentScopeAdmin(): boolean {
    const scope = !!this.areas.groupContext$.value ? this.areas.groupContext$.value : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedAdmin(scope);
  }

  isCurrentScopeWrite(): boolean {
    const scope = !!this.areas.groupContext$.value ? this.areas.groupContext$.value : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedWrite(scope);
  }

  isCurrentScopeRead(): boolean {
    const scope = !!this.areas.groupContext$.value ? this.areas.groupContext$.value : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedRead(scope);
  }

  isCurrentScopeExclusiveReadClient(): boolean {
    if (!this.areas.groupContext$.value) {
      throw new Error('No scope currently active');
    }
    return this.isScopedExclusiveReadClient(this.areas.groupContext$.value);
  }

  isScopedAdmin(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return !!this.userInfoSubject.value.permissions.find((sc) => (sc.scope === null || sc.scope === scope) && this.ge(sc.permission, Permission.ADMIN));
    }
    return false;
  }

  isScopedWrite(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return !!this.userInfoSubject.value.permissions.find((sc) => (sc.scope === null || sc.scope === scope) && this.ge(sc.permission, Permission.WRITE));
    }
    return false;
  }

  isScopedRead(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return !!this.userInfoSubject.value.permissions.find((sc) => (sc.scope === null || sc.scope === scope) && this.ge(sc.permission, Permission.READ));
    }
    return false;
  }

  isScopedExclusiveReadClient(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      // We have either a global or scoped CLIENT permission,
      const clientPerm = this.userInfoSubject.value.permissions.find((sc) => (sc.scope === null || sc.scope === scope) && sc.permission === Permission.CLIENT);
      // ... and there is *NO* other permission on the user.
      const nonClientPerm = this.userInfoSubject.value.permissions.find(
        (sc) => (sc.scope === null || sc.scope === scope) && sc.permission !== Permission.CLIENT
      );
      return !!clientPerm && !nonClientPerm;
    }
    return false;
  }

  private ge(c1: Permission, c2: Permission): boolean {
    switch (c2) {
      case Permission.CLIENT:
        return c1 === Permission.CLIENT || c1 === Permission.READ || c1 === Permission.WRITE || c1 === Permission.ADMIN;
      case Permission.READ:
        return c1 === Permission.READ || c1 === Permission.WRITE || c1 === Permission.ADMIN;
      case Permission.WRITE:
        return c1 === Permission.WRITE || c1 === Permission.ADMIN;
      case Permission.ADMIN:
        return c1 === Permission.ADMIN;
    }
  }

  getUserInfo(): Observable<UserInfo> {
    this.log.debug('Fetching current user info...');
    this.http.get<UserInfo>(this.cfg.config.api + '/auth/user').subscribe((userInfo) => this.userInfoSubject.next(userInfo));
    return this.userInfoSubject.asObservable();
  }

  updateUserInfo(info: UserInfo): Observable<any> {
    this.log.debug('Updating current user info...');
    this.userInfoSubject.next(info);
    return this.http.post<UserInfo>(this.cfg.config.api + '/auth/user', info);
  }

  changePassword(dto: UserChangePasswordDto): Observable<any> {
    this.log.debug('Changing password for current user...');
    return this.http.post(this.cfg.config.api + '/auth/change-password', dto, {
      responseType: 'text',
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  getAuthPackForUser(genFull: boolean): Observable<string> {
    this.log.debug('Retrieve auth pack for user');
    return this.http.get(this.cfg.config.api + '/auth/auth-pack', {
      responseType: 'text',
      params: new HttpParams().append('full', genFull ? 'true' : 'false'),
    });
  }
}
