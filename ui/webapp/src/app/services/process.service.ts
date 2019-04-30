import { HttpClient } from '@angular/common/http';
import { EventEmitter, Injectable } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { ProcessState, ProcessStatusDto } from '../models/gen.dtos';
import { InstanceService } from './instance.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessService {
  constructor(private http: HttpClient, private instanceService: InstanceService) {}

  /** Flag whether or not the status is currently loading */
  public loading = false;

  /** Status of all applications. Key=AppId */
  private app2Status: { [key: string]: ProcessStatusDto } = {};

  /** Status of all applications. Key=InstanceTag */
  private tag2Status: { [key: string]: ProcessStatusDto[] } = {};

  /** Notifies all listener that the status has been loaded */
  private processStatusEmitter = new EventEmitter();

  /**  Asynchronously refreshes the cached process status */
  public refreshStatus(instanceGroup: string, instanceId: string) {
    if (this.loading) {
      return;
    }
    this.loading = true;
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instanceId) + '/processes';
    const promise = this.http.get<{ [key: string]: ProcessStatusDto }>(url);
    promise.pipe(finalize(() => (this.loading = false))).subscribe(result => {
      this.app2Status = result;
      this.tag2Status = this.groupByTag(Object.values(result));
      this.processStatusEmitter.next(result);
    });
  }

  /** Registers a new handler that is notified whenever the process status changes */
  public subscribe(eventHandler: () => void) {
    return this.processStatusEmitter.subscribe(eventHandler);
  }

  /**
   * Returns the status for the given application in the given version.
   */
  public getStatusOfApp(appId: string, instanceTag?: string): ProcessStatusDto {
    const status = this.app2Status[appId];
    if (!status) {
      return null;
    }
    if (instanceTag && status.instanceTag !== instanceTag) {
      return null;
    }
    return status;
  }

  /**
   * Returns the status of all applications in the given version.
   */
  public getStatusOfTag(instanceTag: string): ProcessStatusDto[] {
    return this.tag2Status[instanceTag];
  }

  /**
   * Returns whether or not at least one app is running or waiting in a version NOT equal to the given one.
   */
  public isRunningOutOfSync(instanceTag: string): boolean {
    const states = new Set<ProcessState>([
      ProcessState.RUNNING,
      ProcessState.RUNNING_UNSTABLE,
      ProcessState.CRASHED_WAITING,
    ]);
    for (const status of Object.values(this.app2Status)) {
      if (!states.has(status.processState)) {
        continue;
      }
      if (status.instanceTag !== instanceTag) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns whether or not at least one app is running or scheduled to run in the given version.
   */
  public isRunningOrScheduledInVersion(instanceTag: string): boolean {
    const apps = this.tag2Status[instanceTag];
    if (!apps) {
      return false;
    }
    const states = new Set<ProcessState>([
      ProcessState.RUNNING,
      ProcessState.RUNNING_UNSTABLE,
      ProcessState.CRASHED_WAITING,
    ]);
    const runningApp = apps.find(app => states.has(app.processState));
    if (runningApp) {
      return true;
    }
    return false;
  }

  /**
   * Returns a human readable status tooltip text
   */
  getStatusTooltip(instanceTag: string, appId: string) {
    const status = this.app2Status[appId];
    if (!status) {
      return 'Unknown';
    }
    switch (status.processState) {
      case ProcessState.RUNNING:
      case ProcessState.RUNNING_UNSTABLE:
        return 'Application is running.';
      case ProcessState.STOPPED:
        return 'Application has been stopped.';
      case ProcessState.CRASHED_WAITING:
        return 'The application recently crashed.';
      case ProcessState.CRASHED_PERMANENTLY:
        return 'The application crashed repeatedly.';
    }
    return 'unknown';
  }

  /**
   * Starts the application with the given ID.
   */
  public startProcess(instanceGroup: string, instance: string, processId: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/' + processId + '/start';
    return this.http.get(url);
  }

  /**
   * Stops the application with the given ID.
   */
  public stopProcess(instanceGroup: string, instance: string, processId: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/' + processId + '/stop';
    return this.http.get(url);
  }

  /**
   * Stops and then starts the application with the given ID.
   */
  public restartProcess(instanceGroup: string, instance: string, processId: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/' + processId + '/restart';
    return this.http.get(url);
  }

  /**
   * Starts all applications having start type 'instance' configured.
   */
  public startAll(instanceGroup: string, instance: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/start';
    return this.http.get(url);
  }

  /**
   * Stops all currently running applications.
   */
  public stopAll(instanceGroup: string, instance: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/stop';
    return this.http.get(url);
  }

  /**
   * Stops all and then starts all applications having start type 'instance' configured.
   */
  public restartAll(instanceGroup: string, instance: string) {
    const url = this.instanceService.buildInstanceUrl(instanceGroup, instance) + '/processes/restart';
    return this.http.get(url);
  }

  /**
   * Groups the running applications by their instance tag.
   */
  groupByTag(apps: ProcessStatusDto[]) {
    const result: { [key: string]: ProcessStatusDto[] } = {};
    for (const app of apps) {
      let values = result[app.instanceTag];
      if (!values) {
        values = [];
        result[app.instanceTag] = values;
      }
      values.push(app);
    }
    return result;
  }
}
