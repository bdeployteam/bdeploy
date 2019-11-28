import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ManifestKey } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';
import { DownloadService } from '../../shared/services/download.service';
import { SoftwareRepositoryService } from './software-repository.service';

@Injectable({
  providedIn: 'root',
})
export class SoftwareService {

  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private downloadService: DownloadService,
  ) {}

  public listSoftwares(softwareRepositoryName: string): Observable<ManifestKey[]> {
    const url: string = this.buildSoftwareUrl(softwareRepositoryName);
    this.log.debug('listSoftwares: ' + url);
    return this.http.get<ManifestKey[]>(url);
  }

  public getSoftwareDiskUsage(softwareRepositoryName: string, key: ManifestKey): Observable<string> {
    const url = this.buildSoftwareNameUrl(softwareRepositoryName, key) + '/diskUsage';
    this.log.debug('getSoftwareDiskUsage: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public deleteSoftwareVersion(softwareRepositoryName: string, key: ManifestKey) {
    const url: string = this.buildSoftwareNameTagUrl(softwareRepositoryName, key);
    this.log.debug('deleteSoftwareVersion: ' + url);
    return this.http.delete(url);
  }

  public createSoftwareZip(softwareRepositoryName: string, key: ManifestKey): Observable<string> {
    const url = this.buildSoftwareNameTagUrl(softwareRepositoryName, key) + '/zip';
    this.log.debug('createSoftwareZip: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public downloadSoftware(token: string): string {
    return this.downloadService.createDownloadUrl(token);
  }

  public getSoftwareUploadUrl(softwareRepositoryName: string): string {
    return this.buildSoftwareUrl(softwareRepositoryName) + '/upload';
  }

  private buildSoftwareUrl(softwareRepositoryName: string): string {
    return this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + softwareRepositoryName + '/content';
  }

  private buildSoftwareNameUrl(softwareRepositoryName: string, key: ManifestKey): string {
    return this.buildSoftwareUrl(softwareRepositoryName) + '/' + key.name;
  }

  private buildSoftwareNameTagUrl(softwareRepositoryName: string, key: ManifestKey): string {
    return this.buildSoftwareUrl(softwareRepositoryName) + '/' + key.name + '/' + key.tag;
  }

}
