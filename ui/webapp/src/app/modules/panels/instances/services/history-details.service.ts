import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest, forkJoin } from 'rxjs';
import { finalize, first, map, skipWhile } from 'rxjs/operators';
import { InstanceConfiguration, InstanceNodeConfigurationListDto, InstanceVersionDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { InstanceConfigCache } from '../utils/instance-utils';

@Injectable({
  providedIn: 'root',
})
export class HistoryDetailsService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private groups = inject(GroupsService);
  private instances = inject(InstancesService);

  public loading$ = new BehaviorSubject<boolean>(false);
  public versions$ = new BehaviorSubject<InstanceVersionDto[]>(null);

  private cache: InstanceConfigCache[] = [];

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor() {
    combineLatest([this.instances.current$, this.groups.current$]).subscribe(([instance, group]) => {
      this.cache = [];
      this.versions$.next(null);

      if (!instance || !group) {
        return;
      }

      this.loading$.next(true);
      this.http
        .get<InstanceVersionDto[]>(`${this.apiPath(group.name)}/${instance.instanceConfiguration.id}/versions`)
        .pipe(
          finalize(() => this.loading$.next(false)),
          measure('Load Historic Versions')
        )
        .subscribe((r) => {
          this.versions$.next(r);
        });
    });
  }

  public getVersionDetails(version: string): Observable<InstanceConfigCache> {
    return new Observable<InstanceConfigCache>((s) => {
      this.instances.current$
        .pipe(
          skipWhile((i) => !i),
          first()
        )
        .subscribe((instance) => {
          const group = this.groups.current$.value;

          // check if we have a cache entry already.
          const cached = this.cache.find((c) => c.version === version);
          if (cached) {
            s.next(cached);
            s.complete();
            return;
          }

          let loadConfig: Observable<InstanceConfiguration>;
          let loadNodes: Observable<InstanceNodeConfigurationListDto>;

          if (version === instance.activeVersion?.tag) {
            // instances service loads the active version anyway, no need to do it again.
            loadConfig = this.instances.active$.pipe(
              skipWhile((i) => i === null),
              map((c) => c.instanceConfiguration),
              first()
            );
            loadNodes = this.instances.activeNodeCfgs$.pipe(
              skipWhile((n) => n === null),
              first()
            );
          } else {
            // this is a version we do not normally need, except for history viewing. load it from the server.
            loadConfig = this.http.get<InstanceConfiguration>(
              `${this.apiPath(group.name)}/${instance.instanceConfiguration.id}/${version}`
            );
            loadNodes = this.http.get<InstanceNodeConfigurationListDto>(
              `${this.apiPath(group.name)}/${instance.instanceConfiguration.id}/${version}/nodeConfiguration`
            );
          }

          this.loading$.next(true);
          forkJoin({
            config: loadConfig,
            nodes: loadNodes,
          })
            .pipe(
              finalize(() => this.loading$.next(false)),
              measure('Load Historic Configuration')
            )
            .subscribe({
              next: ({ config, nodes }) => {
                const entry: InstanceConfigCache = { version, config, nodes };
                this.cache.push(entry);
                s.next(entry);
                s.complete();
              },
              error: (error) => {
                s.error(error);
                s.complete();
              },
            });
        });
    });
  }
}
