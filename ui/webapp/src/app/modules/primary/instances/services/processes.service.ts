import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, Observable, Subscription, combineLatest, debounceTime } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  InstanceDto,
  InstanceProcessStatusDto,
  ProcessState,
  ProcessStatusDto,
  VerifyOperationResultDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NO_LOADING_BAR } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { ServersService } from '../../servers/services/servers.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private groups = inject(GroupsService);
  private servers = inject(ServersService);
  private instances = inject(InstancesService);
  private snackbar = inject(MatSnackBar);
  private zone = inject(NgZone);

  public loading$ = new BehaviorSubject<boolean>(true);
  public processStates$ = new BehaviorSubject<{ [key: string]: ProcessStatusDto }>(null);
  public processToNode$ = new BehaviorSubject<{ [key: string]: string }>({});
  public processStatesLoadTime$ = new BehaviorSubject<number>(null);

  private instance: InstanceDto;
  private loadInterval;
  private checkInterval;

  private loadCall: Subscription;
  private loadCancelCount = 0;
  private loadWarnIssued = false;

  public static get(states: { [key: string]: ProcessStatusDto }, processId: string): ProcessStatusDto {
    if (!states) {
      return null;
    }

    return states[processId];
  }

  public static isRunning(status: ProcessState) {
    return (
      status === ProcessState.RUNNING ||
      status === ProcessState.RUNNING_NOT_STARTED ||
      status === ProcessState.RUNNING_STOP_PLANNED ||
      status === ProcessState.RUNNING_UNSTABLE ||
      status === ProcessState.RUNNING_NOT_ALIVE
    );
  }

  public static getPort(states: { [key: number]: boolean }, port: number): boolean {
    if (!states) {
      return false;
    }
    return states[port];
  }

  private apiPath = (group, instance) => `${this.cfg.config.api}/group/${group}/instance/${instance}/processes`;
  private isCentral = false;

  constructor() {
    this.cfg.isCentral$.subscribe((value) => {
      this.isCentral = value;
    });
    // whenever the active instance or servers change, we want to setup things.
    combineLatest([this.servers.servers$, this.instances.active$])
      .pipe(debounceTime(100))
      .subscribe(
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
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
        if (this.loadCancelCount > 0 && this.loadCancelCount % (this.isCentral ? 2 : 3) == 0) {
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
      .get<InstanceProcessStatusDto>(
        `${this.apiPath(group.name, this.instance.instanceConfiguration.id)}`,
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
          this.processToNode$.next(p.processToNode);
          this.processStates$.next(p.processStates);
          this.processStatesLoadTime$.next(Date.now()); // local time wanted.
        });
      });

    if (trackCalls) {
      this.loadCall = call;
    }
  }

  public start(pids: string[]): Observable<any> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/start`, pids)
      .pipe(finalize(() => this.reload()));
  }

  public stop(pids: string[]): Observable<any> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/stop`, pids)
      .pipe(finalize(() => this.reload()));
  }

  public restart(pids: string[]): Observable<any> {
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

  public reinstall(pid: string): Observable<any> {
    return this.http.post<boolean>(
      `${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/reinstall/${pid}`,
      null
    );
  }

  public startInstance(): Observable<any> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/startAll`)
      .pipe(finalize(() => this.reload()));
  }

  public stopInstance(): Observable<any> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/stopAll`)
      .pipe(finalize(() => this.reload()));
  }

  public restartInstance(): Observable<any> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.instance.instanceConfiguration.id)}/restartAll`)
      .pipe(finalize(() => this.reload()));
  }
}
