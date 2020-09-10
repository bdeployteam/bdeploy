import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { CredentialsApi, Permission, UserChangePasswordDto, UserInfo } from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private log: Logger = this.loggingService.getLogger('AuthenticationService');

  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(this.cookies.check('st') ? this.cookies.get('st') : null);

  public userInfoSubject: BehaviorSubject<UserInfo> = new BehaviorSubject(null);

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService, private cookies: CookieService) { }

  authenticate(username: string, password: string): Observable<any> {
    this.log.debug('authenticate("' + username + '", <...>)');

    return this.http.post(this.cfg.config.api + '/auth',
     { user: username, password: password } as CredentialsApi,
     { responseType: 'text', headers: suppressGlobalErrorHandling(new HttpHeaders) }).pipe(
        tap(
          result => {
            this.tokenSubject.next(result);
            // this is required if the backend runs on a different server than the frontend (dev)
            // - don't use secure, as this will fail in the development case (HTTP server only).
            this.cookies.set('st', result, 365, '/', null, false, 'Strict');
          }, error => {
            this.tokenSubject.next(null);
            this.cookies.delete('st', '/');
          }
        ),
        map (
          result => {
            this.log.debug('Fetching current user info...');
            this.http.get<UserInfo>(this.cfg.config.api + '/auth/user').pipe(
              tap(
                userInfo => this.userInfoSubject.next(userInfo)
              )
            );
          }, error => {
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

  private getTokenPayload(): any {
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
  }

  getRecentInstanceGroups(): Observable<string[]> {
    this.log.debug('Fetching recent groups...');
    return this.http.get<string[]>(this.cfg.config.api + '/auth/recent-groups');
  }

  isGlobalAdmin(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.permission === Permission.ADMIN) != null;
    }
    return false;
  }

  isGlobalWrite(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.permission === Permission.WRITE) != null;
    }
    return false;
  }

  isGlobalRead(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.permission === Permission.READ) != null;
    }
    return false;
  }

  isScopedAdmin(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return this.userInfoSubject.value.permissions.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.permission, Permission.ADMIN)) != null;
    }
    return false;
  }

  isScopedWrite(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return this.userInfoSubject.value.permissions.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.permission, Permission.WRITE)) != null;
    }
    return false;
  }

  isScopedRead(scope: string): boolean {
    if (this.userInfoSubject.value && this.userInfoSubject.value.permissions) {
      return this.userInfoSubject.value.permissions.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.permission, Permission.READ)) != null;
    }
    return false;
  }

  private ge(c1: Permission, c2: Permission): boolean {
    return (c2 === Permission.READ)
      || (c1 !== Permission.READ && c2 === Permission.WRITE)
      || (c1 === Permission.ADMIN && c2 === Permission.ADMIN);
  }

  getUserInfo(): Observable<UserInfo> {
    this.log.debug('Fetching current user info...');
    this.http.get<UserInfo>(this.cfg.config.api + '/auth/user').subscribe(
      userInfo => this.userInfoSubject.next(userInfo)
    );
    return this.userInfoSubject.asObservable();
  }

  updateUserInfo(info: UserInfo): Observable<any> {
    this.log.debug('Updating current user info...');
    this.userInfoSubject.next(info);
    return this.http.post<UserInfo>(this.cfg.config.api + '/auth/user', info);
  }

  changePassword(dto: UserChangePasswordDto): Observable<any> {
    this.log.debug('Changing password for current user...');
    return this.http.post(this.cfg.config.api + '/auth/change-password', dto,
    { responseType: 'text', headers: suppressGlobalErrorHandling(new HttpHeaders()) });
  }

  getAuthPackForUser(genFull: boolean): Observable<string> {
    this.log.debug('Retrieve auth pack for user');
    return this.http.get(this.cfg.config.api + '/auth/auth-pack', { responseType: 'text', params: new HttpParams().append('full', genFull ? 'true' : 'false')});
  }

}
