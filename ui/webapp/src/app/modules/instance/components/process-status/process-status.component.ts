import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { MinionMode, ProcessState, ProcessStatusDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { unsubscribe } from '../../../shared/utils/object.utils';
import { ProcessService } from '../../services/process.service';


@Component({
  selector: 'app-process-status',
  templateUrl: './process-status.component.html',
  styleUrls: ['./process-status.component.css'],
})
export class ProcessStatusComponent implements OnInit, OnChanges, OnDestroy {
  private readonly icons: { [key: string]: string } = {};
  private readonly icons_outlined: { [key: string]: string } = {};

  @Input() instanceTag: string;
  @Input() activatedTag: string;
  @Input() appId: string;

  @Input() outOfSyncIcon = false;
  @Input() outOfSyncText = false;
  @Input() iconWhenStopped = false;
  @Input() isDirty = false;

  private subscription: Subscription;
  private processState: ProcessState;
  private processStateTag: string;

  public showIcon: boolean;
  public showOutOfSync: boolean;
  public showOutOfSyncText: boolean;
  public showNotLoadedIcon: boolean;

  public statusIcon: string;
  public statusHint: string;
  public statusClass: string[] = [];
  public statusCount: number;

  constructor(
    private processService: ProcessService,
    private configService: ConfigService,
  ) {
    this.initIcons();
  }

  ngOnInit() {
    this.processState = ProcessState.STOPPED;
    this.subscription = this.processService.subscribe(() => this.onStatusChanged());
  }

  ngOnChanges() {
    this.onStatusChanged();
  }

  ngOnDestroy() {
    unsubscribe(this.subscription);
  }

  initIcons() {
    this.icons[ProcessState.STOPPED] = 'favorite';
    this.icons[ProcessState.RUNNING] = 'favorite';
    this.icons[ProcessState.RUNNING_UNSTABLE] = 'favorite';
    this.icons[ProcessState.CRASHED_WAITING] = 'report_problem';
    this.icons[ProcessState.CRASHED_PERMANENTLY] = 'error';
    this.icons[ProcessState.RUNNING_STOP_PLANNED] = 'schedule';
    this.icons_outlined[ProcessState.STOPPED] = 'favorite_outline';
    this.icons_outlined[ProcessState.RUNNING] = 'favorite_outline';
    this.icons_outlined[ProcessState.RUNNING_UNSTABLE] = 'favorite_outline';
    this.icons_outlined[ProcessState.CRASHED_WAITING] = 'report_problem_outline';
    this.icons_outlined[ProcessState.CRASHED_PERMANENTLY] = 'error_outline';
    this.icons_outlined[ProcessState.RUNNING_STOP_PLANNED] = 'schedule';
  }

  onStatusChanged() {
    this.resetState();

    var status;
    if (this.appId) {
      status = this.processService.getStatusOfApp(this.appId);
      if (status) {
        this.updateSingleState(status);
      }
    } else {
      status = this.processService.getStatusOfTag(this.instanceTag);
      if (status) {
        this.updateMultiState(status);
      }
    }

    this.showIcon = this.getShowIcon();
    this.showNotLoadedIcon = !this.processService.loading && !status && this.isCentral();
    this.showOutOfSyncText = this.getShowOutOfSyncText();
    this.statusIcon = this.getStatusIcon();
    this.statusClass = this.getStatusClass();
    this.statusHint = this.getStatusTooltip();
  }

  resetState() {
    this.showIcon = false;
    this.showNotLoadedIcon = false;
    this.showOutOfSyncText = false;
    this.processState = ProcessState.STOPPED;
  }

  updateSingleState(status: ProcessStatusDto) {
    this.processState = status.processState;
    this.processStateTag = status.instanceTag;
  }

  updateMultiState(status: ProcessStatusDto[]) {
    const runningStates = new Set([ProcessState.RUNNING, ProcessState.RUNNING_UNSTABLE]);
    this.statusCount = status.filter(app => runningStates.has(app.processState)).length;

    // Display if at least one app crashed permanently
    if (status.find(app => ProcessState.CRASHED_PERMANENTLY === app.processState)) {
      this.processState = ProcessState.CRASHED_PERMANENTLY;
      return;
    }

    // Display if at least one app is waiting
    if (status.find(app => ProcessState.CRASHED_WAITING === app.processState)) {
      this.processState = ProcessState.CRASHED_WAITING;
      return;
    }

    // Display if at least one app is about to be stopped
    if (status.find(app => ProcessState.RUNNING_STOP_PLANNED === app.processState)) {
      this.processState = ProcessState.RUNNING_STOP_PLANNED;
      return;
    }

    // Display if at least one app is running
    if (status.find(app => runningStates.has(app.processState))) {
      this.processState = ProcessState.RUNNING;
      return;
    }
  }

  getStatusIcon() {
    if (!this.isMyVersion()) {
      return this.icons_outlined[this.processState];
    }
    return this.icons[this.processState];
  }

  getStatusClass() {
    const styles = ['icon'];
    if (this.isRunning()) {
      styles.push('app-process-running');
    }
    if(this.isStopPlanned()) {
      styles.push('app-process-running');
    }
    if (this.isStopped()) {
      styles.push('app-process-stopped');
    }
    if (this.isCrashedWaiting()) {
      styles.push('app-process-crash');
    }
    if (this.isCrashedPermanently()) {
      styles.push('app-process-crash');
    }
    if (this.getShowOutOfSync() && !this.isMyVersion()) {
      styles.push('app-process-outdated');
    }
    return styles;
  }

  getStatusTooltip() {
    if (this.isCrashedWaiting()) {
      if (this.showStateOfAllApps()) {
        return 'One ore more applications recently crashed.';
      }
      if (this.getShowOutOfSync()) {
        return 'Application recently crashed in a different version.';
      }
      return 'Application recently crashed.';
    }
    if (this.isCrashedPermanently()) {
      if (this.showStateOfAllApps()) {
        return 'One ore more applications permanently crashed.';
      }
      if (this.getShowOutOfSync()) {
        return 'Application crashed permanently in a different version.';
      }
      return 'Application crashed permanently.';
    }
    if(this.isStopPlanned()) {
      if (this.showStateOfAllApps()) {
        return 'One ore more applications are going to be stopped.';
      }
      return 'Application is about to be stopped.';
    }
    if (this.isRunning()) {
      if (this.showStateOfAllApps()) {
        return 'One ore more applications are running.';
      }
      if (this.getShowOutOfSync()) {
        return 'Application is running in a different version.';
      }
      return 'Application is running.';
    }
    if (this.isStopped()) {
      if (this.showStateOfAllApps()) {
        return 'All applications are stopped.';
      }
      return 'Application is stopped';
    }
    return 'unknown';
  }

  showStateOfAllApps() {
    return this.appId === undefined;
  }

  isOutOfSync() {
    if (!this.isRunning()) {
      return false;
    }
    if (this.showStateOfAllApps()) {
      return this.instanceTag !== this.activatedTag;
    }
    return this.processStateTag !== this.activatedTag;
  }

  getShowIcon() {
    if (this.isStopped()) {
      return this.iconWhenStopped;
    }
    return true;
  }

  isMyVersion() {
    if (this.showStateOfAllApps()) {
      return this.activatedTag === this.instanceTag;
    }
    return this.processStateTag === this.instanceTag;
  }

  getShowOutOfSync() {
    if (!this.isOutOfSync()) {
      return false;
    }
    return this.outOfSyncIcon || this.outOfSyncText;
  }

  getShowOutOfSyncText() {
    if (!this.isOutOfSync()) {
      return false;
    }
    return this.outOfSyncText;
  }

  isRunning() {
    return this.processState === ProcessState.RUNNING || this.processState === ProcessState.RUNNING_UNSTABLE;
  }

  isStopped() {
    return this.processState === ProcessState.STOPPED;
  }

  isStopPlanned() {
    return this.processState === ProcessState.RUNNING_STOP_PLANNED;
  }

  isCrashedWaiting() {
    return this.processState === ProcessState.CRASHED_WAITING;
  }

  isCrashedPermanently() {
    return this.processState === ProcessState.CRASHED_PERMANENTLY;
  }

  isCentral() {
    return this.configService.config.mode === MinionMode.CENTRAL;
  }

}
