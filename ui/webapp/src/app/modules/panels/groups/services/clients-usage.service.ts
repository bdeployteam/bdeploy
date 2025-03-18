import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { differenceInDays, parse } from 'date-fns';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { ClientUsageData } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../../primary/groups/services/groups.service';

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
  appId: string;
  usage: ClientUsagePerDay[];
}

const DATE_FORMAT = 'yyyy-MM-dd';

@Injectable({
  providedIn: 'root',
})
export class ClientsUsageService {
  private readonly http = inject(HttpClient);
  private readonly cfg = inject(ConfigService);
  private readonly groups = inject(GroupsService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public clientUsage$ = new BehaviorSubject<ClientUsagePerApp[]>(null);

  private readonly apiPath = (g: string) => `${this.cfg.config.api}/group/${g}/instance`;

  public load(id: string): Observable<ClientUsagePerApp[]> {
    this.loading$.next(true);
    return this.http.get<ClientUsageData>(`${this.apiPath(this.groups.current$.value.name)}/${id}/clientUsage`).pipe(
      finalize(() => this.loading$.next(false)),
      measure('Load Client Usage'),
      map((usage) => this.transform(usage)),
    );
  }

  /**
   * Transforms the raw client usage in something easier consumable by UI components.
   * <p>
   * TODO: This might better be done on the server already.
   */
  private transform(usage: ClientUsageData): ClientUsagePerApp[] {
    const now = Date.now(); // don't care about time drift
    const result: ClientUsagePerApp[] = [];
    for (const day of Object.keys(usage.clientUsage)) {
      const date = parse(day, DATE_FORMAT, new Date());
      const perApp = usage.clientUsage[day];

      // we should only get 30 days, but who knows :)
      if (differenceInDays(now, date) > 30) {
        continue;
      }

      for (const app of Object.keys(perApp)) {
        let appItem = result.find((i) => i.appId === app);
        if (!appItem) {
          appItem = { appId: app, usage: [] };
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
