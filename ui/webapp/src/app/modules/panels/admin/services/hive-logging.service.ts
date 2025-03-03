import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { RemoteDirectory, RemoteDirectoryEntry, StringEntryChunkDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';

@Injectable({
  providedIn: 'root',
})
export class HiveLoggingService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly downloadService = inject(DownloadService);
  private readonly areas = inject(NavAreasService);

  public loading$ = new BehaviorSubject<boolean>(false);
  public directories$ = new BehaviorSubject<RemoteDirectory[]>(null);
  public bhive$ = new BehaviorSubject<string>(null);

  private readonly apiPath = (h: string) => `${this.cfg.config.api}/hive/${h}/logging`;

  constructor() {
    this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params?.['bhive']) {
        return;
      }

      this.bhive$.next(route.params['bhive']);
    });
  }

  public reload() {
    this.directories$.next(null);
    this.loading$.next(true);
    this.http
      .get<RemoteDirectory[]>(`${this.apiPath(this.bhive$.value)}/logDirs`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('List Hive Logs'),
      )
      .subscribe((dirs) => {
        this.bhive$.next(this.bhive$.value);
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
        );
      });
  }

  public downloadLogFileContent(rd: RemoteDirectory, rde: RemoteDirectoryEntry) {
    this.http
      .post(`${this.apiPath(this.bhive$.value)}/request/${rd.minion}`, rde, {
        responseType: 'text',
      })
      .subscribe((token) => {
        this.downloadService.download(`${this.apiPath(this.bhive$.value)}/stream/${token}`);
      });
  }

  public getLogContentChunk(
    rd: RemoteDirectory,
    rde: RemoteDirectoryEntry,
    offset: number,
    limit: number,
    silent: boolean,
  ): Observable<StringEntryChunkDto> {
    return this.http.post<StringEntryChunkDto>(`${this.apiPath(this.bhive$.value)}/content/${rd.minion}`, rde,
      {
        headers: silent ? { ignoreLoadingBar: '' } : null,
        params: new HttpParams().set('offset', offset.toString()).set('limit', limit.toString()),
      }
    );
  }
}
