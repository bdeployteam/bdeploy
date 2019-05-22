import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ManifestKey, NodeStatus } from '../models/gen.dtos';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root'
})
export class UpdateDataService {

  private static BASEPATH = '/swup';
  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
  ) {}

  public listBDeployVersions(): Observable<ManifestKey[]> {
    const url: string = this.cfg.config.api + UpdateDataService.BASEPATH + '/bdeploy';
    this.log.debug('listBDeployVersions: ' + url);
    return this.http.get<ManifestKey[]>(url);
  }

  public listLauncherVersions(): Observable<ManifestKey[]> {
    const url: string = this.cfg.config.api + UpdateDataService.BASEPATH + '/launcher';
    this.log.debug('listLauncherVersions: ' + url);
    return this.http.get<ManifestKey[]>(url);
  }

  public deleteVersion(keys: ManifestKey[]) {
    const url: string = this.cfg.config.api + UpdateDataService.BASEPATH;
    this.log.debug('deleteVersion: ' + url);
    return this.http.post(url, keys);
  }

  public getNodeStates() {
    const url: string = this.cfg.config.api + UpdateDataService.BASEPATH + '/bdeploy/minions';
    this.log.debug('getNodeStates: ' + url);
    return this.http.get<NodeStatus[]>(url);
  }

  public updateBdeploy(keys: ManifestKey[]) {
    const url: string = this.cfg.config.api + UpdateDataService.BASEPATH + '/selfUpdate';
    this.log.debug('updateBdeploy: ' + url);
    return this.http.post(url, keys);
  }

  public getUploadUrl() {
    return this.cfg.config.api + UpdateDataService.BASEPATH;
  }

}
