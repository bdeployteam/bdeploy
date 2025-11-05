import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ManifestKey, OperatingSystem } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

export interface SoftwareVersion {
  version: string;

  system: ManifestKey[];
  launcher: ManifestKey[];

  current: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class SoftwareUpdateService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);

  public loading$ = new BehaviorSubject<boolean>(false);
  public software$ = new BehaviorSubject<SoftwareVersion[]>(null);

  private readonly apiPath = () => `${this.cfg.config.api}/swup`;
  public uploadUrl$ = new BehaviorSubject<string>(this.apiPath());

  public load() {
    this.loading$.next(true);
    forkJoin([this.listBDeployVersions(), this.listLauncherVersions()])
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Load Available Software'),
      )
      .subscribe(([b, l]) => {
        const tags: Record<string, SoftwareVersion> = {};
        b.forEach((key) => {
          const k = tags[key.tag] || {
            version: key.tag,
            system: [],
            launcher: [],
            current: convert2String(this.cfg.config.version) === key.tag,
          };
          k.system.push(key);
          tags[key.tag] = k;
        });

        l.forEach((key) => {
          const k = tags[key.tag] || {
            version: key.tag,
            system: [],
            launcher: [],
            current: convert2String(this.cfg.config.version) === key.tag,
          };
          k.launcher.push(key);
          tags[key.tag] = k;
        });

        this.software$.next(
          Object.values(tags).sort((x, y) => {
            if (x.version > y.version) {
              return -1;
            }
            if (x.version < y.version) {
              return 1;
            }
            return 0;
          }),
        );
      });
  }

  public listBDeployVersions(): Observable<ManifestKey[]> {
    return this.http.get<ManifestKey[]>(`${this.apiPath()}/bdeploy`);
  }

  public listLauncherVersions(): Observable<ManifestKey[]> {
    return this.http.get<ManifestKey[]>(`${this.apiPath()}/launcher`);
  }

  public deleteVersion(keys: ManifestKey[]) {
    return this.http.post(`${this.apiPath()}`, keys);
  }

  public updateBdeploy(keys: ManifestKey[]) {
    return this.http.post(`${this.apiPath()}/selfUpdate`, keys);
  }

  public getDownloadUrl(key: ManifestKey) {
    return `${this.apiPath()}/download/${key.name}/${key.tag}`;
  }

  public createLauncherInstaller(os: OperatingSystem): Observable<string> {
    return this.http.get(`${this.apiPath()}/createLauncherInstaller`, {
      params: new HttpParams().set('os', os.toLowerCase()),
      responseType: 'text',
    });
  }

  public restartServer() {
    return this.http.get(`${this.apiPath()}/restart`);
  }

  public createStackDump() {
    return this.http.get(`${this.apiPath()}/stackdump`);
  }
}
