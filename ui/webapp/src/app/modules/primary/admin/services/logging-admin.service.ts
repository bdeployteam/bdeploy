import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry, StringEntryChunkDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';
import { Logger, LoggingService } from '../../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class LoggingAdminService {
  private readonly log: Logger = this.loggingService.getLogger('LoggingAdminService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService, private downloadService: DownloadService) {}

  private buildLoggingAdminUrl() {
    return this.cfg.config.api + '/logging-admin';
  }

  public listLogDirs(): Observable<RemoteDirectory[]> {
    const url: string = this.buildLoggingAdminUrl() + '/logDirs';
    this.log.debug('listLogDirs: ' + url);
    return this.http.get<RemoteDirectory[]>(url);
  }

  public downloadLogFileContent(rd, rde) {
    const url: string = this.buildLoggingAdminUrl() + '/request/' + rd.minion;
    this.log.debug('downloadLogFileContent');
    this.http.post(url, rde, { responseType: 'text' }).subscribe((token) => {
      this.downloadService.download(this.buildLoggingAdminUrl() + '/stream/' + token);
    });
  }

  public getLogContentChunk(rd: RemoteDirectory, rde: RemoteDirectoryEntry, offset: number, limit: number, silent: boolean): Observable<StringEntryChunkDto> {
    const url: string = this.buildLoggingAdminUrl() + '/content/' + rd.minion;
    this.log.debug('getLogContentChunk: ' + url);
    const options = {
      headers: null,
      params: new HttpParams().set('offset', offset.toString()).set('limit', limit.toString()),
    };
    if (silent) {
      options.headers = { ignoreLoadingBar: '' };
    }
    return this.http.post<StringEntryChunkDto>(url, rde, options);
  }

  public getLogConfig(): Observable<string> {
    const url: string = this.buildLoggingAdminUrl() + '/config';
    this.log.debug('getLogConfig: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public setLogConfig(encoded: string): Observable<any> {
    const url: string = this.buildLoggingAdminUrl() + '/config';
    this.log.debug('setLogConfig: ' + url);
    return this.http.post(url, encoded);
  }
}
