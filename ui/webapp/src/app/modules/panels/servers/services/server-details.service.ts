import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, Observable, Subscriber, Subscription } from 'rxjs';
import { finalize, map, tap } from 'rxjs/operators';
import { InstanceConfiguration, ManagedMasterDto, Version } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { retryWithDelay, suppressGlobalErrorHandling } from 'src/app/modules/core/utils/server.utils';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

// inter-browser support only works with text/plain...
export const ATTACH_MIME_TYPE = 'text/plain';

@Injectable({
  providedIn: 'root',
})
export class ServerDetailsService implements OnDestroy {
  private apiPath = `${this.cfg.config.api}/managed-servers`;
  private serverName$ = new BehaviorSubject<string>(null);

  public loading$ = new BehaviorSubject<boolean>(false);
  public server$ = new BehaviorSubject<ManagedMasterDto>(null);
  public instances$ = new BehaviorSubject<InstanceConfiguration[]>([]);

  private subscription: Subscription;
  private serverSubscription: Subscription;

  constructor(route: ActivatedRoute, private servers: ServersService, private cfg: ConfigService, private http: HttpClient, private areas: NavAreasService) {
    this.subscription = route.paramMap.subscribe((p) => {
      this.serverName$.next(p.get('server'));

      if (!!this.serverSubscription) {
        this.serverSubscription.unsubscribe();
      }
      this.serverSubscription = this.servers.servers$.pipe(map((s) => s.find((e) => e.hostName === this.serverName$.value))).subscribe((server) => {
        this.server$.next(server);
        if (!server) {
          this.instances$.next([]);
          return;
        }

        this.loading$.next(true);
        this.http
          .get<InstanceConfiguration[]>(`${this.apiPath}/controlled-instances/${this.areas.groupContext$.value}/${server.hostName}`)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe((inst) => {
            this.instances$.next(inst);
          });
      });
    });
  }

  ngOnDestroy(): void {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  public delete(server: ManagedMasterDto) {
    return this.http.delete(`${this.apiPath}/delete-server/${this.areas.groupContext$.value}/${server.hostName}`);
  }

  public update(server: ManagedMasterDto) {
    return this.http.post(`${this.apiPath}/update-server/${this.areas.groupContext$.value}/${server.hostName}`, server);
  }

  public remoteUpdateTransfer(server: ManagedMasterDto): Observable<ManagedMasterDto> {
    return new Observable<ManagedMasterDto>((s) => {
      this.http.post(`${this.apiPath}/minion-transfer-updates/${this.areas.groupContext$.value}/${server.hostName}`, server.update).subscribe(
        (trans) => {
          this.servers.synchronize(server).subscribe(
            (sync) => this.complete(sync, s),
            (err) => this.complete(null, s, err)
          );
        },
        (err) => this.complete(null, s, err)
      );
    });
  }

  public remoteUpdateInstall(server: ManagedMasterDto): Observable<Version> {
    return new Observable<Version>((s) => {
      // first transfer if something is missing.
      this.http.post(`${this.apiPath}/minion-transfer-updates/${this.areas.groupContext$.value}/${server.hostName}`, server.update).subscribe(
        (trans) => {
          // second install the update on the minion
          this.http.post(`${this.apiPath}/minion-install-updates/${this.areas.groupContext$.value}/${server.hostName}`, server.update).subscribe(
            (inst) =>
              // third wait for the update to complete, including restart of the server.
              this.waitForUpdate(server).subscribe(
                (v) =>
                  // last synchronize to fetch the updated data.
                  this.servers.synchronize(server).subscribe(
                    (sync) => this.complete(v, s),
                    (err) => this.complete(null, s, err)
                  ),
                (err) => this.complete(null, s, err)
              ),
            (err) => this.complete(null, s, err)
          );
        },
        (err) => this.complete(null, s, err)
      );
    });
  }

  private waitForUpdate(server: ManagedMasterDto): Observable<any> {
    return this.http
      .get<Version>(`${this.apiPath}/minion-ping/${this.areas.groupContext$.value}/${server.hostName}`, {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      })
      .pipe(
        tap((v) => {
          if (!isEqual(v, server.update.updateVersion)) {
            throw new Error(`Server is running but reports the wrong version: ${convert2String(v)}`);
          }
        }),
        retryWithDelay()
      );
  }

  private complete<T>(val: T, obs: Subscriber<T>, err?: any) {
    if (!!err) {
      obs.error(err);
    } else {
      obs.next(val);
    }
    obs.complete();
  }
}
