import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CredentialsApi, LDAPSettingsDto, ObjectChangeType, UserInfo } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { measure } from '../../../core/utils/performance.utils';
import { suppressGlobalErrorHandling } from '../../../core/utils/server.utils';

@Injectable({
  providedIn: 'root',
})
export class AuthAdminService {
  private apiPath = () => `${this.cfg.config.api}/auth/admin`;

  public loading$ = new BehaviorSubject<boolean>(true);
  public users$ = new BehaviorSubject<UserInfo[]>(null);

  constructor(private cfg: ConfigService, private http: HttpClient, changes: ObjectChangesService) {
    changes.subscribe(ObjectChangeType.USER, { scope: [] }, (change) => this.load());
    this.load();
  }

  public load() {
    this.loading$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath()}/users`)
      .pipe(
        measure('Get All Users'),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((users) => this.users$.next(users));
  }

  public createLocalUser(userInfo: UserInfo) {
    return this.http.put(`${this.apiPath()}/local`, userInfo);
  }

  public updateUser(userInfo: UserInfo) {
    return this.http.post(`${this.apiPath()}`, userInfo);
  }

  public deleteUser(name: string) {
    const options = { params: new HttpParams().set('name', name) };
    return this.http.delete(`${this.apiPath()}`, options);
  }

  public updateLocalUserPassword(user: string, password: string) {
    const options = { params: new HttpParams().set('user', user) };
    return this.http.post(`${this.apiPath()}/local/pw`, password, options);
  }

  public traceAuthentication(username: string, password: string): Observable<string[]> {
    return this.http.post<string[]>(`${this.apiPath()}/traceAuthentication`, { user: username, password: password } as CredentialsApi, {
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public testLdapServer(dto: LDAPSettingsDto): Observable<string> {
    return this.http.post(`${this.apiPath()}/testLdapServer`, dto, {
      responseType: 'text',
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }
}
