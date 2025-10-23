import { HttpClient } from '@angular/common/http';
import { inject, Injectable, NgZone } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, combineLatest, debounceTime, Observable, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  ApplicationStartType,
  InstanceDto,
  MappedInstanceProcessStatusDto,
  ProcessState,
  ProcessStatusDto,
  VerifyOperationResultDto
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NO_LOADING_BAR } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { ServersService } from '../../servers/services/servers.service';
import { InstancesService } from './instances.service';


export type StartType = 'Instance' | 'Manual' | 'Confirmed Manual';

@Injectable({
  providedIn: 'root'
})
export class ProcessesService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly groups = inject(GroupsService);
  private readonly servers = inject(ServersService);
  private readonly instances = inject(InstancesService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly zone = inject(NgZone);

  public loading$ = new BehaviorSubject<boolean>(true);
  public processStates$ = new BehaviorSubject<MappedInstanceProcessStatusDto>(null);
  public processStatesLoadTime$ = new BehaviorSubject<number>(null);

  private instance: InstanceDto;
  private loadInterval: ReturnType<typeof setInterval>;
  private checkInterval: ReturnType<typeof setInterval>;

  private loadCall: Subscription;
  private loadCancelCount = 0;
  private loadWarnIssued = false;


  /**
   * Returns a single process status.
   * The node name, if specified will must be a SERVER node, otherwise this will return null.
   * If not specified, it will be assumed this is configured on a SERVER node and if that is
   * not the case, this will return null
   *
   * @param instanceProcessStatus
   * @param appId
   * @param nodeName
   */
  public static get(instanceProcessStatus: MappedInstanceProcessStatusDto, appId: string, nodeName?: string): ProcessStatusDto {
    // if we don't know the states
    if (!instanceProcessStatus) {
      return null;
    }

    const targetNode: string = ProcessesService.identifyServerNode(instanceProcessStatus, appId, nodeName);
    if (!!targetNode && !!instanceProcessStatus.processStates[appId]) {
      return instanceProcessStatus.processStates[appId][targetNode];
    }

    return null;
  }

  public static getAppStates(instanceProcessStatus: MappedInstanceProcessStatusDto, appId: string): Record<string, ProcessStatusDto> {
    // if we don't know the states
    if (!instanceProcessStatus) {
      return null;
    }

    return instanceProcessStatus.processStates[appId];
  }

  public static getNumberOfProcesses(instanceProcessStatus: MappedInstanceProcessStatusDto, appId: string): number {
    const appStates = ProcessesService.getAppStates(instanceProcessStatus, appId);
    return appStates ? Object.entries(appStates).length : 0;
  }

  /**
   * This method ensured that it supplies a server node so that the (appId, result) is valid for doing calls
   * that require to identify a server node.
   * If nodeName is supplied it is assumed the caller already has that name from a context which points to that
   * app making sense on that node, and only a check to make sure that node is a not a multi node will be done.
   *
   * @param instanceProcessStatus
   * @param appId
   * @param nodeName
   */
  public static identifyServerNode(instanceProcessStatus: MappedInstanceProcessStatusDto, appId: string, nodeName?: string): string {
    if (!!appId && !!instanceProcessStatus) {
      // HINT: processToNode contains app->configuredNode mapping and can return a fictional node
      const configuredNode = nodeName ? nodeName : instanceProcessStatus.processToNode[appId];
      return Object.hasOwn(instanceProcessStatus.multiNodeToRuntimeNode, configuredNode) ? null : configuredNode;

    }

    return null;
  }

  public static confirmAppIsConfiguredOnMultiNode(instanceProcessStatus: MappedInstanceProcessStatusDto, appId: string): string {
    if (instanceProcessStatus?.processToNode[appId]) {
      const node = instanceProcessStatus.processToNode[appId];

      return Object.hasOwn(instanceProcessStatus.multiNodeToRuntimeNode, node) ? node : null;
    }

    return null;
  }

  public static isRunning(status: ProcessState): boolean {
    return (
      status === ProcessState.RUNNING ||
      status === ProcessState.RUNNING_NOT_STARTED ||
      status === ProcessState.RUNNING_STOP_PLANNED ||
      status === ProcessState.RUNNING_UNSTABLE ||
      status === ProcessState.RUNNING_NOT_ALIVE
    );
  }

  private readonly apiPath = (group: string, instance: string) =>
    `${this.cfg.config.api}/group/${group}/instance/${instance}/processes`;
  private isCentral = false;

  constructor() {
    this.cfg.isCentral$.subscribe((value) => {
      this.isCentral = value;
    });
    // whenever the active instance or servers change, we want to setup things.
    combineLatest([this.servers.servers$, this.instances.active$])
      .pipe(debounceTime(100))
      .subscribe(
        ([_, instance]) => {
          clearInterval(this.loadInterval);
          clearInterval(this.checkInterval);

          this.instance = instance;

          this.zone.runOutsideAngular(() => {
            // we'll refresh every 30 seconds in case of central & synced, and every 5 seconds in case we're local.
            this.loadInterval = setInterval(() => this.reload(true), this.isCentral ? 15000 : 5000);
            this.checkInterval = setInterval(() => this.checkState(), 1000);
          });

          this.reload();
        }
      );
  }

  private checkState(): boolean {
    if (!this.instance || !this.servers.isSynchronized(this.instance.managedServer)) {
      clearInterval(this.loadInterval);
      clearInterval(this.checkInterval);
      this.processStates$.next(null);
      this.loading$.next(false);
      return false;
    }
    return true;
  }

  /**
   * load the current process state.
   *
   * @param trackCalls whether to keep track of repeated calls and issue warnings if previous calls took too long. this is primarily meant for use when reloading on an interval.
   */
  public reload(trackCalls = false) {
    if (!this.checkState()) {
      return;
    }

    if (trackCalls) {
      if (this.loadCall) {
        // central interval = 15 seconds, so warning every 45 seconds.
        // local interval = 5 seconds, so warning every 20 seconds.
        // (warning after N calls have already been cancelled, so interval * count + 1)
        if (this.loadCancelCount > 0 && this.loadCancelCount % (this.isCentral ? 2 : 3) === 0) {
          if (!this.loadWarnIssued) {
            this.loadWarnIssued = true;
            this.snackbar.open('Process status response seems to be very slow, please be patient.', 'ACKNOWLEDGE');
          }
          return; // not cancelling, not re-calling. lets just wait for at least one to succeed at some point.
        } else {
          this.loadCancelCount++;
          this.loadCall.unsubscribe();
        }
      } else {
        this.loadCancelCount = 0;
      }
      this.loadWarnIssued = false;
    }

    this.zone.run(() => {
      this.loading$.next(true);
    });

    const group = this.groups.current$.value;
    const call = this.http
      .get<MappedInstanceProcessStatusDto>(
        `${this.apiPath(group.name, this.instance.instanceConfiguration.id)}/mapped`,
        NO_LOADING_BAR
      )
      .pipe(
        finalize(() => {
          this.zone.run(() => {
            if (trackCalls) {
              this.loadCall = null;
            }
            this.loading$.next(false);
          });
        }),
        measure('Load Process States')
      )
      .subscribe((p) => {
        this.zone.run(() => {
          this.processStates$.next(p);
          this.processStatesLoadTime$.next(Date.now()); // local time wanted.
        });
      });

    if (trackCalls) {
      this.loadCall = call;
    }
  }

  public start(pids: string[]): Observable<unknown> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/start`, pids)
      .pipe(finalize(() => this.reload()));
  }

  public stop(pids: string[]): Observable<unknown> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/stop`, pids)
      .pipe(finalize(() => this.reload()));
  }

  public restart(pids: string[]): Observable<unknown> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/restart`, pids)
      .pipe(finalize(() => this.reload()));
  }

  public verify(pid: string): Observable<VerifyOperationResultDto> {
    return this.http.post<VerifyOperationResultDto>(
      `${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/verify/${pid}`,
      null
    );
  }

  public reinstall(pid: string): Observable<unknown> {
    return this.http.post<boolean>(
      `${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/reinstall/${pid}`,
      null
    );
  }

  public startInstance(): Observable<unknown> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/startAll`)
      .pipe(finalize(() => this.reload()));
  }

  public stopInstance(): Observable<unknown> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/stopAll`)
      .pipe(finalize(() => this.reload()));
  }

  public restartInstance(): Observable<unknown> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/restartAll`)
      .pipe(finalize(() => this.reload()));
  }

  public static formatStartType(type: ApplicationStartType): StartType {
    switch (type) {
      case ApplicationStartType.INSTANCE:
        return 'Instance';
      case ApplicationStartType.MANUAL:
        return 'Manual';
      case ApplicationStartType.MANUAL_CONFIRM:
        return 'Confirmed Manual';
    }
  }
}
