import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  CredentialsApi,
  LDAPSettingsDto,
  ObjectChangeType,
  UserGroupInfo,
  UserInfo,
} from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from '../../../core/services/object-changes.service';
import { measure } from '../../../core/utils/performance.utils';
import { suppressGlobalErrorHandling } from '../../../core/utils/server.utils';

@Injectable({
  providedIn: 'root',
})
export class AuthAdminService {
  private apiPath = () => `${this.cfg.config.api}/auth/admin`;

  public loadingUsers$ = new BehaviorSubject<boolean>(true);
  public loadingUserGroups$ = new BehaviorSubject<boolean>(true);
  public users$ = new BehaviorSubject<UserInfo[]>(null);
  public userGroups$ = new BehaviorSubject<UserGroupInfo[]>(null);

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    changes: ObjectChangesService
  ) {
    changes.subscribe(ObjectChangeType.USER, EMPTY_SCOPE, () =>
      this.loadUsers()
    );
    changes.subscribe(ObjectChangeType.USER_GROUP, EMPTY_SCOPE, () =>
      this.loadUserGroups()
    );
    this.loadUsers();
    this.loadUserGroups();
  }

  public loadUsers() {
    this.loadingUsers$.next(true);
    this.http
      .get<UserInfo[]>(`${this.apiPath()}/users`)
      .pipe(
        measure('Get All Users'),
        finalize(() => this.loadingUsers$.next(false))
      )
      .subscribe((users) => this.users$.next(users));
  }

  public createLocalUser(userInfo: UserInfo): Observable<any> {
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

  public loadUserGroups() {
    this.loadingUserGroups$.next(true);
    this.http
      .get<UserGroupInfo[]>(`${this.apiPath()}/user-groups`)
      .pipe(
        measure('Get All User Groups'),
        finalize(() => this.loadingUserGroups$.next(false))
      )
      .subscribe((userGroups) => this.userGroups$.next(userGroups));
  }

  public createUserGroup(userGroupInfo: UserGroupInfo): Observable<any> {
    return this.http.put(`${this.apiPath()}/user-groups`, userGroupInfo);
  }

  public updateUserGroup(userGroupInfo: UserGroupInfo) {
    return this.http.post(`${this.apiPath()}/user-groups`, userGroupInfo);
  }

  public deleteUserGroup(group: string) {
    return this.http.delete(`${this.apiPath()}/user-groups/${group}`);
  }

  public addUserToGroup(group: string, user: string) {
    return this.http.post(
      `${this.apiPath()}/user-groups/${group}/users/${user}`,
      null
    );
  }

  public removeUserFromGroup(group: string, user: string) {
    return this.http.delete(
      `${this.apiPath()}/user-groups/${group}/users/${user}`
    );
  }

  public traceAuthentication(
    username: string,
    password: string
  ): Observable<string[]> {
    return this.http.post<string[]>(
      `${this.apiPath()}/traceAuthentication`,
      { user: username, password: password } as CredentialsApi,
      {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      }
    );
  }

  public testLdapServer(dto: LDAPSettingsDto): Observable<string> {
    return this.http.post(`${this.apiPath()}/testLdapServer`, dto, {
      responseType: 'text',
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public importAccountsLdapServer(dto: LDAPSettingsDto): Observable<string> {
    return this.http.post(`${this.apiPath()}/import-ldap-accounts`, dto, {
      responseType: 'text',
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }
}
