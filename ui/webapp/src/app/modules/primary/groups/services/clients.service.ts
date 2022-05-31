import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, forkJoin, Observable, of, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  ClientApplicationDto,
  InstanceClientAppsDto,
  InstanceConfiguration,
  InstanceUiEndpointsDto,
  LauncherDto,
  ObjectChangeType,
  OperatingSystem,
  UiEndpointDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from './groups.service';

export interface ClientApp {
  instance: InstanceConfiguration;
  client: ClientApplicationDto;
  endpoint: UiEndpointDto;
}

@Injectable({
  providedIn: 'root',
})
export class ClientsService {
  public loading$ = new BehaviorSubject<boolean>(true);
  public launcher$ = new BehaviorSubject<LauncherDto>(null);
  public apps$ = new BehaviorSubject<ClientApp[]>(null);

  private subscription: Subscription;
  private apiSwupPath = `${this.cfg.config.api}/swup`;
  private apiGroupPath = (g) => `${this.cfg.config.api}/group/${g}`;
  private apiInstancePath = (g) => `${this.apiGroupPath(g)}/instance`;

  constructor(
    private http: HttpClient,
    private cfg: ConfigService,
    private groups: GroupsService,
    private downloads: DownloadService,
    private changes: ObjectChangesService
  ) {
    this.groups.current$.subscribe((g) =>
      this.updateChangeSubscription(g?.name)
    );
  }

  private reload(group: string) {
    if (!group) {
      this.apps$.next([]);
      return;
    }

    this.loading$.next(true);
    forkJoin({
      launchers: this.http.get<LauncherDto>(
        `${this.apiSwupPath}/launcherLatest`
      ),
      apps: this.http.get<InstanceClientAppsDto[]>(
        `${this.apiGroupPath(group)}/client-apps`
      ),
      uiEps: this.http.get<InstanceUiEndpointsDto[]>(
        `${this.apiGroupPath(group)}/ui-endpoints`
      ),
    })
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Load Client Launchers and Applications')
      )
      .subscribe((result) => {
        this.launcher$.next(result.launchers);

        const r: ClientApp[] = [];
        for (const inst of result.apps) {
          r.push(
            ...inst.applications.map((a) => ({
              instance: inst.instance,
              client: a,
              endpoint: null,
            }))
          );
        }
        for (const eps of result.uiEps) {
          r.push(
            ...eps.endpoints.map((e) => ({
              instance: eps.instance,
              client: null,
              endpoint: e,
            }))
          );
        }
        this.apps$.next(r);
      });
  }

  private updateChangeSubscription(group: string) {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    if (group) {
      this.subscription = this.changes.subscribe(
        ObjectChangeType.INSTANCE,
        { scope: [group] },
        () => {
          this.reload(group);
        }
      );
    }

    this.reload(group);
  }

  public hasLauncher(os: OperatingSystem): boolean {
    return (
      !!this.launcher$.value &&
      !!this.launcher$.value.launchers &&
      !!this.launcher$.value.launchers[os]
    );
  }

  /** Downloads the Click & Start file for the given client application */
  public downloadClickAndStart(
    app: string,
    name: string,
    instance: string
  ): Observable<any> {
    return new Observable((s) => {
      this.http
        .get(
          `${this.apiInstancePath(
            this.groups.current$.value.name
          )}/${instance}/${app}/clickAndStart`
        )
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
  public downloadInstaller(app: string, instance: string): Observable<any> {
    return new Observable((s) => {
      this.http
        .get(
          `${this.apiInstancePath(
            this.groups.current$.value.name
          )}/${instance}/${app}/installer/zip`,
          {
            responseType: 'text',
          }
        )
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
  public downloadLauncherInstaller(os: OperatingSystem): Observable<any> {
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

  /** Download the unbranded launcher as ZIP */
  public downloadLauncherZip(os: OperatingSystem): Observable<any> {
    const launchers = this.launcher$.value?.launchers;
    if (!launchers || !launchers[os]) {
      return of(null);
    }

    // TODO: To allow better progress reporting, we should use our prepare/token/download mechanism instead.
    this.downloads.download(
      `${this.apiSwupPath}/download/${launchers[os].name}/${launchers[os].tag}`
    );
    return of(null);
  }

  public getDirectUiURI(app: ClientApp): Observable<string> {
    return this.http.get(
      `${this.apiInstancePath(this.groups.current$.value.name)}/${
        app.instance.uuid
      }/uiDirect/${app.endpoint.uuid}/${app.endpoint.endpoint.id}`,
      { responseType: 'text' }
    );
  }
}
