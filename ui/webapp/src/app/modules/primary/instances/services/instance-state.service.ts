import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { mergeMap, publishReplay, refCount } from 'rxjs/operators';
import { InstanceDto, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceStateService {
  public state$: Observable<InstanceStateRecord>;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private instances: InstancesService
  ) {
    this.state$ = this.instances.current$.pipe(
      mergeMap((i) => this.getLoadCall(i)),
      publishReplay(1),
      refCount()
    );
  }

  private getLoadCall(i: InstanceDto): Observable<InstanceStateRecord> {
    return !i
      ? of(null)
      : this.http
          .get<InstanceStateRecord>(
            `${this.apiPath(this.groups.current$.value.name)}/${
              i.instanceConfiguration.uuid
            }/state`
          )
          .pipe(measure('Load Instance Version States'));
  }

  public install(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/install`
      )
      .pipe(
        measure(
          `Install ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }

  public uninstall(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/uninstall`
      )
      .pipe(
        measure(
          `Uninstall ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }

  public activate(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/activate`
      )
      .pipe(
        measure(
          `Activate ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }
}
