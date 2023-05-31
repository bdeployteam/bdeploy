import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { AuthService } from '@auth0/auth0-angular';
import { CookieService } from 'ngx-cookie-service';
import {
  BehaviorSubject,
  Observable,
  ReplaySubject,
  combineLatest,
  of,
} from 'rxjs';
import {
  catchError,
  finalize,
  first,
  map,
  skipWhile,
  tap,
} from 'rxjs/operators';
import {
  ApiAccessToken,
  CredentialsApi,
  Permission,
  SpecialAuthenticators,
  UserChangePasswordDto,
  UserInfo,
} from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ConfigService } from './config.service';
import { NavAreasService } from './nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(
    this.cookies.check('st') ? this.cookies.get('st') : null
  );

  private userInfoSubject$: ReplaySubject<UserInfo> = new ReplaySubject(1);
  private currentUserInfo: UserInfo = null; // possibly uninitialized value
  get firstUserInfo$(): Observable<UserInfo> {
    return this.userInfoSubject$.pipe(first());
  }

  public isCurrentScopedExclusiveReadClient$: BehaviorSubject<boolean> =
    new BehaviorSubject(false);
  public isCurrentScopeWrite$: BehaviorSubject<boolean> = new BehaviorSubject(
    false
  );
  public isCurrentScopeAdmin$: BehaviorSubject<boolean> = new BehaviorSubject(
    false
  );
  public isGlobalAdmin$: BehaviorSubject<boolean> = new BehaviorSubject(false);
  public isGlobalExclusiveReadClient$: BehaviorSubject<boolean> =
    new BehaviorSubject(false);

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private cookies: CookieService,
    private areas: NavAreasService,
    private injector: Injector
  ) {
    this.userInfoSubject$.subscribe((userInfo) => {
      const wasEmpty = !this.currentUserInfo;
      this.currentUserInfo = userInfo;
      if (wasEmpty) {
        this.auth0Validate();
      }
    });

    combineLatest([
      areas.groupContext$,
      areas.repositoryContext$,
      this.tokenSubject, // just used as trigger
    ]).subscribe(([groupContext, repositoryContext]) => {
      if (groupContext || repositoryContext) {
        this.isCurrentScopeAdmin$.next(this.isCurrentScopeAdmin());
        this.isCurrentScopeWrite$.next(this.isCurrentScopeWrite());
      } else {
        this.isCurrentScopeAdmin$.next(this.isGlobalAdmin());
        this.isCurrentScopeWrite$.next(this.isGlobalWrite());
      }
      if (groupContext) {
        this.isCurrentScopedExclusiveReadClient$.next(
          this.isCurrentScopeExclusiveReadClient()
        );
      }
    });

    this.tokenSubject.subscribe(() => {
      this.isGlobalAdmin$.next(this.isGlobalAdmin());
      this.isGlobalExclusiveReadClient$.next(
        this.isGlobalExclusiveReadClient()
      );
    });
  }

  authenticate(
    username: string,
    password: string,
    auth?: SpecialAuthenticators
  ): Observable<any> {
    return this.http
      .post(
        this.cfg.config.api + '/auth',
        { user: username, password: password } as CredentialsApi,
        {
          responseType: 'text',
          headers: suppressGlobalErrorHandling(new HttpHeaders()),
          params: auth ? new HttpParams().set('auth', auth) : undefined,
        }
      )
      .pipe(
        tap({
          next: (result) => {
            this.tokenSubject.next(result);
            // this is required if the backend runs on a different server than the frontend (dev)
            // - don't use secure, as this will fail in the development case (HTTP server only).
            this.cookies.set('st', result, 365, '/', null, false, 'Strict');
          },
          error: () => {
            this.tokenSubject.next(null);
            this.cookies.delete('st', '/');
          },
        }),
        map(
          () => {
            this.http
              .get<UserInfo>(this.cfg.config.api + '/auth/user')
              .pipe(tap((userInfo) => this.userInfoSubject$.next(userInfo)));
          },
          () => {
            this.userInfoSubject$.next(null);
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
    const payload: any =
      this.tokenSubject && this.tokenSubject.value
        ? JSON.parse(atob(this.tokenSubject.value)).p
        : null;
    return payload ? JSON.parse(atob(payload)) : null;
  }

  getCurrentUsername(): string {
    if (this.currentUserInfo) {
      return this.currentUserInfo.name;
    }
    const tokenPayload = this.getTokenPayload();
    return tokenPayload ? tokenPayload.it : null;
  }

  auth0Validate() {
    const svc = this.injector.get(AuthService);
    svc.isLoading$.pipe(skipWhile((l) => l)).subscribe(() => {
      if (this.currentUserInfo.externalSystem !== 'AUTH0') {
        return;
      }
      svc
        .getAccessTokenSilently()
        .pipe(
          catchError((err) => {
            console.log('auth0 expired', err);
            this.logout();
            return of(null);
          })
        )
        .subscribe((t) => {
          // if we got one, we want to use it on the server as well.
          this.authenticate(
            this.currentUserInfo.name,
            t,
            SpecialAuthenticators.AUTH0
          )
            .pipe(
              catchError((err) => {
                console.log('cannot update auth0 token on server', err);
                return of(err);
              })
            )
            .subscribe(() => {
              console.log('auth0 token updated on server');
            });
        });
    });
  }

  private auth0Logout() {
    if (
      this.cfg.webAuthCfg?.auth0?.enabled &&
      this.currentUserInfo?.externalSystem === 'AUTH0'
    ) {
      return this.injector.get(AuthService).logout();
    }
    return of(null);
  }

  logout(): Observable<void> {
    this.tokenSubject.next(null);
    this.cookies.delete('st', '/');

    return this.auth0Logout().pipe(
      finalize(() => {
        window.location.reload();
      })
    );
  }

  getRecentInstanceGroups(): Observable<string[]> {
    return of([]);
  }

  isGlobalAdmin(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find(
        (c) => c.scope === null && this.ge(c.permission, Permission.ADMIN)
      );
    }
    return false;
  }

  isGlobalWrite(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find(
        (c) => c.scope === null && this.ge(c.permission, Permission.WRITE)
      );
    }
    return false;
  }

  isGlobalRead(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return !!tokenPayload.c.find(
        (c) => c.scope === null && this.ge(c.permission, Permission.READ)
      );
    }
    return false;
  }

  isGlobalExclusiveReadClient(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      // if it has a CLIENT permission
      const clientPerm = tokenPayload.c.find(
        (c) => c.scope === null && c.permission === Permission.CLIENT
      );
      // and *NO* other global permissions
      const nonClientPerm = tokenPayload.c.find(
        (c) => c.scope === null && c.permission !== Permission.CLIENT
      );

      return !!clientPerm && !nonClientPerm;
    }
    return false;
  }

  private isCurrentScopeAdmin(): boolean {
    const scope = this.areas.groupContext$.value
      ? this.areas.groupContext$.value
      : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedAdmin(scope, this.currentUserInfo);
  }

  isCurrentScopeWrite(): boolean {
    const scope = this.areas.groupContext$.value
      ? this.areas.groupContext$.value
      : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedWrite(scope, this.currentUserInfo);
  }

  isCurrentScopeRead(): boolean {
    const scope = this.areas.groupContext$.value
      ? this.areas.groupContext$.value
      : this.areas.repositoryContext$.value;
    if (!scope) {
      throw new Error('No scope currently active');
    }
    return this.isScopedRead(scope, this.currentUserInfo);
  }

  isCurrentScopeExclusiveReadClient(): boolean {
    if (!this.areas.groupContext$.value) {
      throw new Error('No scope currently active');
    }
    return this.isScopedExclusiveReadClient(this.areas.groupContext$.value);
  }

  isScopedAdmin$(scope: string): Observable<boolean> {
    return this.firstUserInfo$.pipe(
      map((userInfo) => this.isScopedAdmin(scope, userInfo))
    );
  }

  isScopedWrite$(scope: string): Observable<boolean> {
    return this.firstUserInfo$.pipe(
      map((userInfo) => this.isScopedWrite(scope, userInfo))
    );
  }

  isScopedRead$(scope: string): Observable<boolean> {
    return this.firstUserInfo$.pipe(
      map((userInfo) => this.isScopedRead(scope, userInfo))
    );
  }

  private isScopedAdmin(scope: string, userInfo: UserInfo): boolean {
    return this.isScoped(scope, userInfo, Permission.ADMIN);
  }

  private isScopedWrite(scope: string, userInfo: UserInfo): boolean {
    return this.isScoped(scope, userInfo, Permission.WRITE);
  }

  private isScopedRead(scope: string, userInfo: UserInfo): boolean {
    return this.isScoped(scope, userInfo, Permission.READ);
  }

  private isScoped(
    scope: string,
    userInfo: UserInfo,
    permission: Permission
  ): boolean {
    if (userInfo && userInfo.permissions) {
      return !!userInfo.permissions.find(
        (sc) =>
          (sc.scope === null || sc.scope === scope) &&
          this.ge(sc.permission, permission)
      );
    }
    return false;
  }

  isScopedExclusiveReadClient(scope: string): boolean {
    if (this.currentUserInfo && this.currentUserInfo.permissions) {
      // We have either a global or scoped CLIENT permission,
      const clientPerm = this.currentUserInfo.permissions.find(
        (sc) =>
          (sc.scope === null || sc.scope === scope) &&
          sc.permission === Permission.CLIENT
      );
      // ... and there is *NO* other permission on the user.
      const nonClientPerm = this.currentUserInfo.permissions.find(
        (sc) =>
          (sc.scope === null || sc.scope === scope) &&
          sc.permission !== Permission.CLIENT
      );
      return !!clientPerm && !nonClientPerm;
    }
    return false;
  }

  private ge(c1: Permission, c2: Permission): boolean {
    switch (c2) {
      case Permission.CLIENT:
        return (
          c1 === Permission.CLIENT ||
          c1 === Permission.READ ||
          c1 === Permission.WRITE ||
          c1 === Permission.ADMIN
        );
      case Permission.READ:
        return (
          c1 === Permission.READ ||
          c1 === Permission.WRITE ||
          c1 === Permission.ADMIN
        );
      case Permission.WRITE:
        return c1 === Permission.WRITE || c1 === Permission.ADMIN;
      case Permission.ADMIN:
        return c1 === Permission.ADMIN;
    }
  }

  getUserInfo(): Observable<UserInfo> {
    this.http
      .get<UserInfo>(this.cfg.config.api + '/auth/user')
      .subscribe((userInfo) => this.userInfoSubject$.next(userInfo));
    return this.userInfoSubject$.asObservable();
  }

  updateUserInfo(info: UserInfo): Observable<any> {
    this.userInfoSubject$.next(info);
    return this.http.post<UserInfo>(this.cfg.config.api + '/auth/user', info);
  }

  changePassword(dto: UserChangePasswordDto): Observable<any> {
    return this.http.post(this.cfg.config.api + '/auth/change-password', dto, {
      responseType: 'text',
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  getAuthPackForUser(genFull: boolean): Observable<string> {
    return this.http.get(this.cfg.config.api + '/auth/auth-pack', {
      responseType: 'text',
      params: new HttpParams().append('full', genFull ? 'true' : 'false'),
    });
  }
}
