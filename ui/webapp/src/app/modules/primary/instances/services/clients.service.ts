import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { differenceInDays, parse } from 'date-fns';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationConfiguration, ClientUsageData } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

export interface ClientUsagePerHost {
  hostname: string;
  usage: number;
}

export interface ClientUsagePerDay {
  key: string;
  day: Date;
  usage: ClientUsagePerHost[];
}

export interface ClientUsagePerApp {
  appUid: string;
  usage: ClientUsagePerDay[];
}

const DATE_FORMAT = 'yyyy-MM-dd';

@Injectable({
  providedIn: 'root',
})
export class ClientsService {
  public loading$ = new BehaviorSubject<boolean>(true);
  public clientUsage$ = new BehaviorSubject<ClientUsagePerApp[]>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private http: HttpClient,
    private cfg: ConfigService,
    private groups: GroupsService,
    private instances: InstancesService,
    private downloads: DownloadService
  ) {
    // we're using the node states purely as a trigger to reload client usage, as they should share the reload cycle (and node states only when synchyronized)
    combineLatest([this.instances.active$, this.instances.activeNodeStates$]).subscribe(([active, states]) => {
      if (!active || !states) {
        this.clientUsage$.next(null);
        return;
      }

      this.loading$.next(true);
      this.http
        .get<ClientUsageData>(`${this.apiPath(this.groups.current$.value.name)}/${active.instanceConfiguration.uuid}/clientUsage`)
        .pipe(
          finalize(() => this.loading$.next(false)),
          measure('Load Client Usage')
        )
        .subscribe((usage) => {
          this.clientUsage$.next(this.transform(usage));
        });
    });
  }

  /** Downloads the Click & Start file for the given client application */
  public downloadClickAndStart(app: ApplicationConfiguration) {
    this.http
      .get(`${this.apiPath(this.groups.current$.value.name)}/${this.instances.active$.value.instanceConfiguration.uuid}/${app.uid}/clickAndStart`)
      .subscribe((data) => {
        this.downloads.downloadJson(app.name + '.bdeploy', data);
      });
  }

  /** Download the Installer application for the given client application */
  public downloadInstaller(app: ApplicationConfiguration) {
    this.http
      .get(`${this.apiPath(this.groups.current$.value.name)}/${this.instances.active$.value.instanceConfiguration.uuid}/${app.uid}/installer/zip`, {
        responseType: 'text',
      })
      .subscribe((data) => {
        this.downloads.download(this.downloads.createDownloadUrl(data));
      });
  }

  /**
   * Transforms the raw client usage in something easier consumable by UI components.
   * <p>
   * TODO: This might better be done on the server already.
   */
  private transform(usage: ClientUsageData): ClientUsagePerApp[] {
    const now = Date.now();
    const result: ClientUsagePerApp[] = [];
    for (const day of Object.keys(usage.clientUsage)) {
      const date = parse(day, DATE_FORMAT, new Date());
      const perApp = usage.clientUsage[day];

      // we should only get 30 days, but who knows :)
      if (differenceInDays(now, date) > 30) {
        continue;
      }

      for (const app of Object.keys(perApp)) {
        let appItem = result.find((i) => i.appUid === app);
        if (!appItem) {
          appItem = { appUid: app, usage: [] };
          result.push(appItem);
        }

        let dayItem = appItem.usage.find((i) => i.key === day);
        if (!dayItem) {
          dayItem = { key: day, day: date, usage: [] };
          appItem.usage.push(dayItem);
        }

        const perHost = perApp[app];
        for (const host of Object.keys(perHost)) {
          const count = perHost[host];

          let hostItem = dayItem.usage.find((i) => i.hostname === host);
          if (!hostItem) {
            hostItem = { hostname: host, usage: 0 };
            dayItem.usage.push(hostItem);
          }

          hostItem.usage += count;
        }
      }
    }

    return result;
  }
}
