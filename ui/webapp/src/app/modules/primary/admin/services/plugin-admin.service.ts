import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ObjectChangeType, PluginInfoDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from '../../../core/services/object-changes.service';
import { measure } from '../../../core/utils/performance.utils';

@Injectable({
  providedIn: 'root',
})
export class PluginAdminService {
  private readonly http = inject(HttpClient);
  private readonly cfg = inject(ConfigService);
  private readonly changes = inject(ObjectChangesService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public plugins$ = new BehaviorSubject<PluginInfoDto[]>([]);

  private readonly apiPath = () => `${this.cfg.config.api}/plugin-admin`;
  public globalUploadUrl$ = new BehaviorSubject<string>(`${this.apiPath()}/upload-global`);

  constructor() {
    this.changes.subscribe(ObjectChangeType.PLUGIN, EMPTY_SCOPE, () => {
      this.reload();
    });

    this.reload();
  }

  private reload() {
    this.loading$.next(true);
    this.http
      .get<PluginInfoDto[]>(`${this.apiPath()}/list`)
      .pipe(
        measure('Load Plugins'),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe((p) => this.plugins$.next(p));
  }

  public loadGlobalPlugin(dto: PluginInfoDto) {
    this.http.post(`${this.apiPath()}/load-global`, dto.id).subscribe();
  }

  public unloadPlugin(dto: PluginInfoDto) {
    this.http.post(`${this.apiPath()}/unload`, dto.id).subscribe();
  }

  public deleteGlobalPlugin(dto: PluginInfoDto) {
    this.http.post(`${this.apiPath()}/delete-global`, dto.id).subscribe();
  }
}
