import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceClientAppsDto, InstanceGroupConfiguration, InstanceGroupPermissionDto, OperatingSystem, UserInfo } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';

@Injectable({
  providedIn: 'root',
})
export class InstanceGroupService {
  public static BASEPATH = '/group';

  private readonly log: Logger = this.loggingService.getLogger('InstanceGroupService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public listInstanceGroups(): Observable<InstanceGroupConfiguration[]> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH;
    this.log.debug('listInstanceGroups: ' + url);
    return this.http.get<InstanceGroupConfiguration[]>(url);
  }

  public listClientApps(name: string, os: OperatingSystem): Observable<InstanceClientAppsDto[]> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/client-apps';
    const options = {
      params: new HttpParams().set('os', os.toUpperCase())
    };
    this.log.debug('listClientApps: ' + url);
    return this.http.get<InstanceClientAppsDto[]>(url, options);
  }

  public createInstanceGroup(group: InstanceGroupConfiguration) {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH;
    this.log.debug('createInstanceGroup: ' + url);
    return this.http.put(url, group);
  }

  /**
   * load an instance group.
   * <p>
   * Does NOT handle errors globally to allow custom handling.
   */
  public getInstanceGroup(name: string): Observable<InstanceGroupConfiguration> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name;
    this.log.debug('getInstanceGroup: ' + url);
    return this.http.get<InstanceGroupConfiguration>(url, {
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public updateInstanceGroup(name: string, group: InstanceGroupConfiguration) {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name;
    this.log.debug('updateInstanceGroup: ' + url);
    return this.http.post(url, group);
  }

  public deleteInstanceGroup(name: string) {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name;
    this.log.debug('deleteInstanceGroup: ' + url);
    return this.http.delete(url);
  }

  public getAllUsers(name: string): Observable<UserInfo[]> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/users';
    this.log.debug('getAllUsers: ' + url);
    return this.http.get<UserInfo[]>(url);
  }

  public updateInstanceGroupPermissions(name: string, permissions: InstanceGroupPermissionDto[]) {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/permissions';
    this.log.debug('updateInstanceGroupPermissions: ' + url);
    return this.http.post(url, permissions);
  }


  public createUuid(name: string): Observable<string> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/new-uuid';
    this.log.debug('createUuid: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public getInstanceGroupImage(name: string) {
    const url: string = this.getInstanceGroupImageUrl(name);
    this.log.debug('getInstanceGroupImage: ' + url);
    return this.http.get(url, { responseType: 'blob' });
  }

  public getInstanceGroupImageUrl(name: string) {
    return this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/image';
  }

  public updateInstanceGroupImage(name: string, file: File): Observable<Response> {
    const url: string = this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + name + '/image';
    this.log.debug('updateInstanceGroupImage: ' + url);
    const formData = new FormData();
    formData.append('image', file, file.name);
    return this.http.post<Response>(url, formData);
  }

}
