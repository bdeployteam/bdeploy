import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpErrorHandlerInterceptor } from '../interceptors/error-handler.interceptor';
import { CredentialsDto } from '../models/gen.dtos';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private log: Logger = this.loggingService.getLogger('AuthenticationService');

  private tokenSubject: BehaviorSubject<string> = new BehaviorSubject(localStorage.getItem('st'));
  private username: string;

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) { }

  authenticate(username: string, password: string): Observable<any> {
    this.log.debug('authenticate("' + username + '", <...>)');

    const x = this.http.post(this.cfg.config.api + '/auth',
     { user: username, password: password } as CredentialsDto,
     { responseType: 'text', headers: HttpErrorHandlerInterceptor.suppressGlobalErrorHandling(new HttpHeaders) });
    x.subscribe(result => {
      this.tokenSubject.next(result);
      localStorage.setItem('st', result);
      this.username = username;
    }, error => {
      this.tokenSubject.next(null);
      localStorage.removeItem('st');
      this.username = null;
    });
    return x;
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
    this.log.info('destroying session for user "' + this.username + '"');
    this.tokenSubject.next(null);
    localStorage.removeItem('st');
  }

  getRecentInstanceGroups(): Observable<String[]> {
    this.log.debug('Fetching recent groups...');
    return this.http.get<String[]>(this.cfg.config.api + '/auth/recent-groups');
  }

  isAdmin(): boolean {
    // TODO: implement :)
    return false; // for testing
  }

}
