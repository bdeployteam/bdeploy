import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SoftwareRepositoryConfiguration } from '../models/gen.dtos';
import { ConfigService } from './config.service';
import { Logger, LoggingService } from './logging.service';
import { UploadService } from './upload.service';

@Injectable({
  providedIn: 'root',
})
export class SoftwareRepositoryService {

  public static BASEPATH = '/softwarerepository';

  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private uploadService: UploadService,
  ) {}

  public listSoftwareRepositories(): Observable<SoftwareRepositoryConfiguration[]> {
    const url: string = this.cfg.config.api + SoftwareRepositoryService.BASEPATH;
    this.log.debug('listSoftwareRepositories: ' + url);
    return this.http.get<SoftwareRepositoryConfiguration[]>(url);
  }

}
