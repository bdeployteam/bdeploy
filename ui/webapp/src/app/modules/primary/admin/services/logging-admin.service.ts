import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { RemoteDirectory, RemoteDirectoryEntry, StringEntryChunkDto } from 'src/app/models/gen.dtos';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';

@Injectable({
  providedIn: 'root',
})
export class LoggingAdminService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly downloadService = inject(DownloadService);

  public loading$ = new BehaviorSubject<boolean>(false);
  public directories$ = new BehaviorSubject<RemoteDirectory[]>([]);

  private readonly apiPath = () => `${this.cfg.config.api}/logging-admin`;

  public reload() {
    this.loading$.next(true);
    this.http
      .get<RemoteDirectory[]>(`${this.apiPath()}/logDirs`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('List Server Logs'),
      )
      .subscribe((dirs) =>
        this.directories$.next(
          dirs.sort((a, b) => {
            if (a.minion === 'master') {
              return -1;
            } else if (b.minion === 'master') {
              return 1;
            } else {
              return a.minion.toLocaleLowerCase().localeCompare(b.minion.toLocaleLowerCase());
            }
          }),
        ),
      );
  }

  public downloadLogFileContent(remoteDirectory: RemoteDirectory, remoteDirectoryEntry: RemoteDirectoryEntry) {
    this.http
      .post(`${this.apiPath()}/request/${remoteDirectory.minion}`, remoteDirectoryEntry, {
        responseType: 'text',
      })
      .subscribe((token) => {
        this.downloadService.download(`${this.apiPath()}/stream/${token}`);
      });
  }

  public getLogContentChunk(
    rd: RemoteDirectory,
    rde: RemoteDirectoryEntry,
    offset: number,
    limit: number,
    silent: boolean,
  ): Observable<StringEntryChunkDto> {
    return this.http.post<StringEntryChunkDto>(
      `${this.apiPath()}/content/${rd.minion}`, rde, {
        headers: silent ? { ignoreLoadingBar: '' } : null,
        params: new HttpParams().set('offset', offset.toString()).set('limit', limit.toString()),
      }
    );
  }

  public getLogConfig(): Observable<string> {
    return this.http.get(`${this.apiPath()}/config`, { responseType: 'text' });
  }

  public setLogConfig(encoded: string): Observable<unknown> {
    return this.http.post(`${this.apiPath()}/config`, encoded);
  }
}
