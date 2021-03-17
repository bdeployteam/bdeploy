import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { InstanceConfiguration, ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

// inter-browser support only works with text/plain...
export const ATTACH_MIME_TYPE = 'text/plain';

@Injectable()
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
}
