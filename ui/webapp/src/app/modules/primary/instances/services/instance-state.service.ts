import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, ReplaySubject, combineLatest, of } from 'rxjs';
import { mergeMap, share } from 'rxjs/operators';
import { InstanceDto, InstanceGroupConfiguration, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceStateService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private groups = inject(GroupsService);
  private instances = inject(InstancesService);

  public state$: Observable<InstanceStateRecord>;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor() {
    this.state$ = combineLatest([
      this.instances.current$,
      this.groups.current$,
      this.instances.instances$, // trigger to reload on changes.
    ]).pipe(
      mergeMap(([i, g, is]) => this.getLoadCall(i, g, is)),
      share({
        connector: () => new ReplaySubject(1),
        resetOnError: true,
        resetOnComplete: false,
        resetOnRefCountZero: false,
      }),
    );
  }

  private getLoadCall(
    i: InstanceDto,
    g: InstanceGroupConfiguration,
    is: InstanceDto[],
  ): Observable<InstanceStateRecord> {
    return !i || !g || !is?.some((instance) => instance.instanceConfiguration.id === i.instanceConfiguration.id) // sometimes instance is removed from instances$ faster than current$ is nulled
      ? of(null)
      : this.http
          .get<InstanceStateRecord>(`${this.apiPath(g.name)}/${i.instanceConfiguration.id}/state`)
          .pipe(measure('Load Instance Version States'));
  }

  public install(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.id
        }/${version}/install`,
      )
      .pipe(measure(`Install ${this.instances.current$.value.instanceConfiguration.id} Version ${version}`));
  }

  public uninstall(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.id
        }/${version}/uninstall`,
      )
      .pipe(measure(`Uninstall ${this.instances.current$.value.instanceConfiguration.id} Version ${version}`));
  }

  public activate(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.id
        }/${version}/activate`,
      )
      .pipe(measure(`Activate ${this.instances.current$.value.instanceConfiguration.id} Version ${version}`));
  }
}
