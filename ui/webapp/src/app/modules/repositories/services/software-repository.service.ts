import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SoftwareRepositoryConfiguration, UserInfo, UserPermissionUpdateDto } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class SoftwareRepositoryService {
  public static BASEPATH = '/softwarerepository';

  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public listSoftwareRepositories(): Observable<SoftwareRepositoryConfiguration[]> {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH;
    this.log.debug('listSoftwareRepositories: ' + url);
    return this.http.get<SoftwareRepositoryConfiguration[]>(url);
  }

  public createSoftwareRepository(group: SoftwareRepositoryConfiguration) {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH;
    this.log.debug('createSoftwareRepository: ' + url);
    return this.http.put(url, group);
  }

  public getSoftwareRepository(name: string): Observable<SoftwareRepositoryConfiguration> {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + name;
    this.log.debug('getSoftwareRepository: ' + url);
    return this.http.get<SoftwareRepositoryConfiguration>(url);
  }

  public updateSoftwareRepository(name: string, group: SoftwareRepositoryConfiguration) {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + name;
    this.log.debug('updateSoftwareRepository: ' + url);
    return this.http.post(url, group);
  }

  public deleteSoftwareRepository(name: string) {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + name;
    this.log.debug('deleteSoftwareRepository: ' + url);
    return this.http.delete(url);
  }

  public getAllUsers(name: string): Observable<UserInfo[]> {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + name + '/users';
    this.log.debug('getAllUsers: ' + url);
    return this.http.get<UserInfo[]>(url);
  }

  public updateSoftwareRepositoryPermissions(name: string, permissions: UserPermissionUpdateDto[]) {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + name + '/permissions';
    this.log.debug('updateSoftwareRepositoryPermissions: ' + url);
    return this.http.post(url, permissions);
  }
}
