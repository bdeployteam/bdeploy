import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  ProcessDetailDto,
  RemoteDirectory,
  RemoteDirectoryEntry,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NO_LOADING_BAR } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessDetailsService implements OnDestroy {
  public loading$ = new BehaviorSubject<boolean>(true);
  public processDetail$ = new BehaviorSubject<ProcessDetailDto>(null);
  public processConfig$ = new BehaviorSubject<ApplicationConfiguration>(null);

  private subscription: Subscription;
  private detailsCall: Subscription;

  private apiPath = (group, instance) =>
    `${this.cfg.config.api}/group/${group}/instance/${instance}`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private instances: InstancesService,
    private processes: ProcessesService,
    private areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.processes.processStates$,
      this.instances.active$,
      this.instances.activeNodeCfgs$,
    ]).subscribe(([route, states, instance, nodes]) => {
      // don't subscribe to processToNode$ separately, as it will always trigger twice together with process states.
      const app2node = this.processes.processToNode$.value;
      const process = route?.params['process'];
      if (!process || !app2node || !app2node[process] || !instance || !nodes) {
        this.processConfig$.next(null);
        this.processDetail$.next(null);
        this.loading$.next(false);
        return;
      }

      this.loading$.next(true);

      // find the configuration for the application we're showing details for
      const appsPerNode = nodes.nodeConfigDtos.map((x) =>
        x?.nodeConfiguration?.applications
          ? x.nodeConfiguration.applications
          : []
      );
      const allApps: ApplicationConfiguration[] = [].concat(...appsPerNode);
      const app = allApps.find((a) => a?.id === process);

      this.processConfig$.next(app);

      if (!states || !states[process]) {
        this.processDetail$.next(null);
        return;
      }

      // if we're already performing a call, cancel it - only one detail at a time.
      this.detailsCall?.unsubscribe();

      // now load the status details and popuplate the service data.
      this.detailsCall = this.http
        .get<ProcessDetailDto>(
          `${this.apiPath(
            this.groups.current$.value.name,
            instance.instanceConfiguration.id
          )}/processes/${app2node[process]}/${process}`,
          NO_LOADING_BAR
        )
        .pipe(
          finalize(() => {
            this.detailsCall = null;
            this.loading$.next(false);
          }),
          measure(`Process Details`)
        )
        .subscribe((d) => {
          this.processDetail$.next(d);
        });
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public writeStdin(value: string) {
    const detail = this.processDetail$.value;

    if (!detail.hasStdin) {
      return;
    }

    this.http
      .post(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instances.active$.value.instanceConfiguration.id
        )}/processes/${detail.status.appId}/stdin`,
        value
      )
      .subscribe();
  }

  public getOutputEntry(): Observable<[RemoteDirectory, RemoteDirectoryEntry]> {
    const detail = this.processDetail$.value;

    return this.http
      .get<RemoteDirectory>(
        `${this.apiPath(
          this.groups.current$.value.name,
          detail.status.instanceId
        )}/output/${detail.status.instanceTag}/${detail.status.appId}`,
        NO_LOADING_BAR
      )
      .pipe(
        map((e) => {
          return [e, e.entries[0]];
        })
      );
  }
}
