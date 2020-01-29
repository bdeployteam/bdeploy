import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Capability, CredentialsDto, UserChangePasswordDto, UserInfo } from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private log: Logger = this.loggingService.getLogger('AuthenticationService');

  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(this.cookies.check('st') ? this.cookies.get('st') : null);

  public userInfo: UserInfo;

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService, private cookies: CookieService) { }

  authenticate(username: string, password: string): Observable<any> {
    this.log.debug('authenticate("' + username + '", <...>)');

    return this.http.post(this.cfg.config.api + '/auth',
     { user: username, password: password } as CredentialsDto,
     { responseType: 'text', headers: suppressGlobalErrorHandling(new HttpHeaders) }).pipe(
        tap(
          result => {
            this.tokenSubject.next(result);
            // this is required if the backend runs on a different server than the frontend (dev)
            this.cookies.set('st', result, 365, '/');
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
                userInfo => this.userInfo = userInfo
              )
            );
          }, error => {
            this.userInfo = null;
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
    if (this.userInfo) {
      return this.userInfo.name;
    }
    const tokenPayload = this.getTokenPayload();
    return tokenPayload ? tokenPayload.it : null;
  }

  logout(): void {
    this.log.info('destroying session for user "' + this.getUsername() + '"');
    this.tokenSubject.next(null);
    this.cookies.delete('st', '/');
  }

  getRecentInstanceGroups(): Observable<String[]> {
    this.log.debug('Fetching recent groups...');
    return this.http.get<String[]>(this.cfg.config.api + '/auth/recent-groups');
  }

  isGlobalAdmin(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.capability === Capability.ADMIN) != null;
    }
    return false;
  }

  isGlobalWrite(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.capability === Capability.WRITE) != null;
    }
    return false;
  }

  isGlobalRead(): boolean {
    const tokenPayload = this.getTokenPayload();
    if (tokenPayload && tokenPayload.c) {
      return tokenPayload.c.find(c => c.scope === null && c.capability === Capability.READ) != null;
    }
    return false;
  }

  isScopedAdmin(scope: string): boolean {
    if (this.userInfo) {
      return this.userInfo.capabilities.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.capability, Capability.ADMIN)) != null;
    }
    return false;
  }

  isScopedWrite(scope: string): boolean {
    if (this.userInfo) {
      return this.userInfo.capabilities.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.capability, Capability.WRITE)) != null;
    }
    return false;
  }

  isScopedRead(scope: string): boolean {
    if (this.userInfo) {
      return this.userInfo.capabilities.find(sc =>
        (sc.scope === null || sc.scope === scope)
        && this.ge(sc.capability, Capability.READ)) != null;
    }
    return false;
  }

  private ge(c1: Capability, c2: Capability): boolean {
    return (c2 === Capability.READ)
      || (c1 !== Capability.READ && c2 === Capability.WRITE)
      || (c1 === Capability.ADMIN && c2 === Capability.ADMIN);
  }

  getUserInfo(): Observable<UserInfo> {
    if (this.userInfo) {
      return of(this.userInfo);
    } else {
      this.log.debug('Fetching current user info...');
      return this.http.get<UserInfo>(this.cfg.config.api + '/auth/user').pipe(
        tap(userInfo => this.userInfo = userInfo)
      );
    }
  }

  updateUserInfo(info: UserInfo): Observable<any> {
    this.log.debug('Updating current user info...');
    this.userInfo = info;
    return this.http.post<UserInfo>(this.cfg.config.api + '/auth/user', info);
  }

  changePassword(dto: UserChangePasswordDto): Observable<any> {
    this.log.debug('Changing password for current user...');
    return this.http.post(this.cfg.config.api + '/auth/change-password', dto,
    { responseType: 'text', headers: suppressGlobalErrorHandling(new HttpHeaders()) });
  }

  getAuthPackForUser(): Observable<String> {
    this.log.debug('Retrieve auth pack for user');
    return this.http.get(this.cfg.config.api + '/auth/auth-pack', { responseType: 'text'});
  }

}
