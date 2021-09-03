import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CredentialsApi, LDAPSettingsDto, UserInfo } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { measure } from '../../core/utils/performance.utils';
import { suppressGlobalErrorHandling } from '../../core/utils/server.utils';

@Injectable({
  providedIn: 'root',
})
export class AuthAdminService {
  private apiPath = () => `${this.cfg.config.api}/auth/admin`;

  constructor(private cfg: ConfigService, private http: HttpClient) {}

  public getAll(): Observable<UserInfo[]> {
    return this.http.get<UserInfo[]>(`${this.apiPath()}/users`).pipe(measure('Get All Users'));
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
