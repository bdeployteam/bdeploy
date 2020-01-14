import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { CredentialsDto, UserChangePasswordDto, UserInfo } from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private log: Logger = this.loggingService.getLogger('AuthenticationService');

  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(this.cookies.check('st') ? this.cookies.get('st') : null);
  private username: string;

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
            this.username = username;
          }, error => {
            this.tokenSubject.next(null);
            this.cookies.delete('st', '/');
            this.username = null;
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

  getUsername(): string {
    if (!this.username) {
      const payload: any = JSON.parse(atob(this.tokenSubject.value)).p;
      if (payload) {
        this.username = JSON.parse(atob(payload)).it;
      }
    }
    return this.username;
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

  isAdmin(): boolean {
    // TODO: implement :)
    return false; // for testing
  }

  getUserInfo(): Observable<UserInfo> {
    this.log.debug('Fetching current user info...');
    return this.http.get<UserInfo>(this.cfg.config.api + '/auth/user');
  }

  updateUserInfo(info: UserInfo): Observable<any> {
    this.log.debug('Updating current user info...');
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
