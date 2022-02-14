import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  InstanceDto,
  ProcessState,
  ProcessStatusDto,
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
  loading$ = new BehaviorSubject<boolean>(true);
  processStates$ = new BehaviorSubject<{ [key: string]: ProcessStatusDto }>(
    null
  );
  processStatesLoadTime$ = new BehaviorSubject<number>(null);

  private instance: InstanceDto;
  private loadInterval;
  private checkInterval;

  public static get(
    states: { [key: string]: ProcessStatusDto },
    processId: string
  ): ProcessStatusDto {
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

  public static getPort(
    states: { [key: number]: boolean },
    port: number
  ): boolean {
    if (!states) {
      return false;
    }
    return states[port];
  }

  private apiPath = (group, instance) =>
    `${this.cfg.config.api}/group/${group}/instance/${instance}/processes`;
  private isCentral = false;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private servers: ServersService,
    private instances: InstancesService
  ) {
    this.cfg.isCentral$.subscribe((value) => {
      this.isCentral = value;
    });
    // whenever the active instance or servers change, we want to setup things.
    combineLatest([this.servers.servers$, this.instances.active$]).subscribe(
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      ([_, instance]) => {
        clearInterval(this.loadInterval);
        clearInterval(this.checkInterval);

        this.instance = instance;

        // we'll refresh every 30 seconds in case of central & synced, and every 5 seconds in case we're local.
        this.loadInterval = setInterval(
          () => this.reload(),
          this.isCentral ? 30000 : 5000
        );
        this.checkInterval = setInterval(() => this.checkState(), 1000);

        this.reload();
      }
    );
  }

  private checkState(): boolean {
    if (
      !this.instance ||
      !this.servers.isSynchronized(this.instance.managedServer)
    ) {
      clearInterval(this.loadInterval);
      clearInterval(this.checkInterval);
      this.processStates$.next(null);
      this.loading$.next(false);
      return false;
    }
    return true;
  }

  public reload() {
    if (!this.checkState()) {
      return;
    }

    const group = this.groups.current$.value;
    this.http
      .get<{ [key: string]: ProcessStatusDto }>(
        `${this.apiPath(group.name, this.instance.instanceConfiguration.uuid)}`,
        NO_LOADING_BAR
      )
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Load Process States')
      )
      .subscribe((p) => {
        this.processStates$.next(p);
        this.processStatesLoadTime$.next(Date.now()); // local time wanted.
      });
  }

  public start(pid: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/${pid}/start`
      )
      .pipe(finalize(() => this.reload()));
  }

  public stop(pid: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/${pid}/stop`
      )
      .pipe(finalize(() => this.reload()));
  }

  public restart(pid: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/${pid}/restart`
      )
      .pipe(finalize(() => this.reload()));
  }

  public startInstance(): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/start`
      )
      .pipe(finalize(() => this.reload()));
  }

  public stopInstance(): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/stop`
      )
      .pipe(finalize(() => this.reload()));
  }

  public restartInstance(): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instance.instanceConfiguration.uuid
        )}/restart`
      )
      .pipe(finalize(() => this.reload()));
  }
}
