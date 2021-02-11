import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Observable, Subject } from 'rxjs';
import { ManifestKey, OperatingSystem, UploadInfoDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { UploadService } from '../../../legacy/shared/services/upload.service';
import { suppressGlobalErrorHandling } from '../../../legacy/shared/utils/server.utils';
import { SoftwareRepositoryService } from './software-repository.service';

export enum ImportState {
  /** Import in progress */
  IMPORTING,
  /** Import finished. No errors reported  */
  FINISHED,
  /** Import failed. */
  FAILED,
}

/** Status of each file imported */
export class ImportStatus {
  filename: string;

  /** Current state */
  state: ImportState;

  /** Notification when the state changes */
  stateObservable: Observable<ImportState>;

  /** The error message if failed */
  detail: any;
}

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
    private uploadService: UploadService,
    private deviceService: DeviceDetectorService
  ) {}

  public listSoftwares(
    softwareRepositoryName: string,
    listProducts: boolean,
    listGeneric: boolean
  ): Observable<ManifestKey[]> {
    const url: string = this.buildSoftwareUrl(softwareRepositoryName);
    let params = new HttpParams();
    params = listProducts ? params.set('products', 'true') : params;
    params = listGeneric ? params.set('generic', 'true') : params;
    this.log.debug('listSoftwares: ' + url);
    return this.http.get<ManifestKey[]>(url, { params: params });
  }

  public getSoftwareDiskUsage(softwareRepositoryName: string, manifestkeyName: string): Observable<string> {
    const url = this.buildSoftwareUrl(softwareRepositoryName) + '/' + manifestkeyName + '/diskUsage';
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

  public upload(softwareRepositoryName: string, files: File[]) {
    const url: string = this.buildSoftwareUrl(softwareRepositoryName) + '/upload-raw-content';
    this.log.debug('upload: ' + url);
    return this.uploadService.upload(url, files, [], 'file');
  }

  public import(softwareRepositoryName: string, dtos: UploadInfoDto[]): Map<string, ImportStatus> {
    const result: Map<string, ImportStatus> = new Map();

    const url: string = this.buildSoftwareUrl(softwareRepositoryName) + '/import-raw-content';

    for (let i = 0; i < dtos.length; i++) {
      const importStatus = new ImportStatus();
      const stateSubject = new Subject<ImportState>();
      importStatus.filename = dtos[i].filename;
      importStatus.stateObservable = stateSubject.asObservable();
      importStatus.stateObservable.subscribe((state) => {
        importStatus.state = state;
      });
      result.set(dtos[i].filename, importStatus);
      stateSubject.next(ImportState.IMPORTING);

      this.http
        .post<UploadInfoDto>(url, dtos[i], { headers: suppressGlobalErrorHandling(new HttpHeaders()) })
        .subscribe(
          (dto) => {
            dtos[i].details = dto.details;
            stateSubject.next(ImportState.FINISHED);
            stateSubject.complete();
          },
          (error) => {
            dtos[i].details = error.statusText + ' (Status ' + error.status + ')';
            stateSubject.next(ImportState.FAILED);
            stateSubject.complete();
          }
        );
    }
    return result;
  }

  public getSoftwareUploadUrl(softwareRepositoryName: string): string {
    return this.buildSoftwareUrl(softwareRepositoryName) + '/upload';
  }

  private buildSoftwareUrl(softwareRepositoryName: string): string {
    return this.cfg.config.api + SoftwareRepositoryService.BASEPATH + '/' + softwareRepositoryName + '/content';
  }

  private buildSoftwareNameTagUrl(softwareRepositoryName: string, key: ManifestKey): string {
    return this.buildSoftwareUrl(softwareRepositoryName) + '/' + key.name + '/' + key.tag;
  }

  public getRunningOs() {
    const runningOs = this.deviceService.os;
    if (runningOs === 'Windows') {
      return OperatingSystem.WINDOWS;
    } else if (runningOs === 'Linux') {
      return OperatingSystem.LINUX;
    } else if (runningOs === 'Mac') {
      return OperatingSystem.MACOS;
    }
    return OperatingSystem.UNKNOWN;
  }
}
