import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { debounceTime, finalize, skipWhile, take } from 'rxjs/operators';
import {
  ClientApplicationDto,
  InstanceAllClientsDto,
  LauncherDto,
  ObjectChangeType,
  OperatingSystem,
  UiEndpointDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { suppressGlobalErrorHandling } from 'src/app/modules/core/utils/server.utils';
import { InstancesService } from '../../instances/services/instances.service';
import { GroupsService } from './groups.service';

export interface ClientApp {
  instanceId: string;
  instanceName: string;
  client: ClientApplicationDto;
  endpoint: UiEndpointDto;
}

@Injectable({
  providedIn: 'root',
})
export class ClientsService {
  private readonly http = inject(HttpClient);
  private readonly cfg = inject(ConfigService);
  private readonly groups = inject(GroupsService);
  private readonly downloads = inject(DownloadService);
  private readonly changes = inject(ObjectChangesService);
  private readonly instances = inject(InstancesService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public launcher$ = new BehaviorSubject<LauncherDto>(null);
  public apps$ = new BehaviorSubject<ClientApp[]>(null);

  private readonly update$ = new BehaviorSubject<string>(null);
  private loadCall: Subscription;

  private subscription: Subscription;
  private readonly apiSwupPath = `${this.cfg.config.api}/swup`;
  private readonly apiGroupPath = (g) => `${this.cfg.config.api}/group/${g}`;
  private readonly apiInstancePath = (g) => `${this.apiGroupPath(g)}/instance`;

  constructor() {
    this.groups.current$.subscribe((g) => this.updateChangeSubscription(g?.name));

    // debounce updates in case multiple instances updated in a single go.
    this.update$.pipe(debounceTime(100)).subscribe((g) => this.reload(g));
  }

  private reload(group: string) {
    if (!group) {
      this.apps$.next([]);
      return;
    }

    // cancel previous calls.
    this.loadCall?.unsubscribe();

    this.loading$.next(true);
    this.loadCall = this.http
      .get<InstanceAllClientsDto>(`${this.apiGroupPath(group)}/all-clients`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Load all Clients'),
      )
      .subscribe((result) => {
        this.launcher$.next(result.launchers);

        // immediate in most cases, but waits for initial load.
        this.instances.instances$
          .pipe(
            skipWhile((i) => !i?.length),
            take(1),
          )
          .subscribe((insts) => {
            const r: ClientApp[] = [];
            for (const inst of result.clients) {
              const iname = insts.find((i) => i.instanceConfiguration?.id === inst.instanceId)?.instanceConfiguration
                ?.name;
              r.push(
                ...inst.applications.map((a) => ({
                  instanceId: inst.instanceId,
                  instanceName: iname,
                  client: a,
                  endpoint: null,
                })),
              );
            }
            for (const eps of result.endpoints) {
              const iname = insts.find((i) => i.instanceConfiguration?.id === eps.instanceId)?.instanceConfiguration
                ?.name;
              r.push(
                ...eps.endpoints.map((e) => ({
                  instanceId: eps.instanceId,
                  instanceName: iname,
                  client: null,
                  endpoint: e,
                })),
              );
            }
            this.apps$.next(r);
          });
      });
  }

  private updateChangeSubscription(group: string) {
    this.subscription?.unsubscribe();

    if (group) {
      this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE, { scope: [group] }, () => {
        this.update$.next(group);
      });
    }

    this.update$.next(group);
  }

  public hasLauncher(os: OperatingSystem): boolean {
    return !!this.launcher$.value && !!this.launcher$.value.launchers && !!this.launcher$.value.launchers[os];
  }

  /** Downloads the Click & Start file for the given client application */
  public downloadClickAndStart(app: string, name: string, instance: string): Observable<unknown> {
    return new Observable((s) => {
      this.http
        .get(`${this.apiInstancePath(this.groups.current$.value.name)}/${instance}/${app}/clickAndStart`)
        .subscribe({
          next: (data) => {
            this.downloads.downloadJson(name + '.bdeploy', data);
            s.next(null);
            s.complete();
          },
          error: (err) => {
            s.error(err);
            s.complete();
          },
        });
    });
  }

  /** Download the Installer application for the given client application */
  public downloadInstaller(app: string, instance: string): Observable<unknown> {
    return new Observable((s) => {
      this.http
        .get(`${this.apiInstancePath(this.groups.current$.value.name)}/${instance}/${app}/installer/zip`, {
          responseType: 'text',
        })
        .subscribe({
          next: (token) => {
            this.downloads.download(this.downloads.createDownloadUrl(token));
            s.next(null);
            s.complete();
          },
          error: (err) => {
            s.error(err);
            s.complete();
          },
        });
    });
  }

  /** Download the unbranded launcher installer which will just install the launcher without concrete application */
  public downloadLauncherInstaller(os: OperatingSystem): Observable<unknown> {
    return new Observable((s) => {
      this.http
        .get(`${this.apiSwupPath}/createLauncherInstaller`, {
          params: { os: os.toLowerCase() },
          responseType: 'text',
        })
        .subscribe({
          next: (token) => {
            this.downloads.download(this.downloads.createDownloadUrl(token));
            s.next(null);
            s.complete();
          },
          error: (err) => {
            s.error(err);
            s.complete();
          },
        });
    });
  }

  public getDirectUiURI(app: ClientApp): Observable<string> {
    return this.http.get(
      `${this.apiInstancePath(this.groups.current$.value.name)}/${app.instanceId}/uiDirect/${app.endpoint.id}/${
        app.endpoint.endpoint.id
      }`,
      {
        responseType: 'text',
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      },
    );
  }
}
