import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { UserInfo } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class AuthAdminService {
  private static BASEPATH = '/auth/admin';
  private log: Logger = this.loggingService.getLogger('AuthAdminService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public getAll(): Observable<UserInfo[]> {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH + '/users';
    this.log.debug('getAll: ' + url);
    return this.http.get<UserInfo[]>(url);
  }

  public createLocalUser(userInfo: UserInfo) {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH + '/local';
    this.log.debug('createLocalUser: ' + url);
    return this.http.put(url, userInfo);
  }

  public updateUser(userInfo: UserInfo) {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH;
    this.log.debug('updateUser: ' + url);
    return this.http.post(url, userInfo);
  }

  public deleteUser(name: string) {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH;
    const options = { params: new HttpParams().set('name', name) };
    this.log.debug('deleteUser: ' + url);
    return this.http.delete(url, options);
  }

  public updateLocalUserPassword(user: string, password: string) {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH + '/local/pw';
    const options = { params: new HttpParams().set('user', user) };
    this.log.debug('updateLocalUserPassword: ' + url);
    return this.http.post(url, password, options);
  }

  public createUuid(): Observable<string> {
    const url: string = this.cfg.config.api + AuthAdminService.BASEPATH + '/new-uuid';
    this.log.debug('createUuid: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }
}
