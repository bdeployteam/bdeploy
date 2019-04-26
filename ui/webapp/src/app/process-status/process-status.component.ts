import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ProcessState, ProcessStatusDto } from '../models/gen.dtos';
import { ProcessService } from '../services/process.service';
import { unsubscribe } from '../utils/object.utils';

@Component({
  selector: 'app-process-status',
  templateUrl: './process-status.component.html',
  styleUrls: ['./process-status.component.css'],
})
export class ProcessStatusComponent implements OnInit, OnChanges, OnDestroy {
  @Input() instanceTag: string;
  @Input() activatedTag: string;
  @Input() appId: string;

  @Input() outOfSyncIcon = false;
  @Input() outOfSyncText = false;
  @Input() iconWhenStopped = false;

  private subscription: Subscription;
  private processState: ProcessState;
  private processStateTag: string;

  public showIcon: boolean;
  public showOutOfSync: boolean;
  public showOutOfSyncText: boolean;

  public statusIcon: string;
  public statusHint: string;
  public statusClass: string[] = [];
  public statusCount: number;

  constructor(private processService: ProcessService) {}

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

  onStatusChanged() {
    this.resetState();

    if (this.appId) {
      const status = this.processService.getStatusOfApp(this.appId);
      if (status) {
        this.updateSingleState(status);
      }
    } else {
      const status = this.processService.getStatusOfTag(this.instanceTag);
      if (status) {
        this.updateMultiState(status);
      }
    }

    this.showIcon = this.getShowIcon();
    this.showOutOfSyncText = this.getShowOutOfSyncText();
    this.statusIcon = this.getStatusIcon();
    this.statusClass = this.getStatusClass();
    this.statusHint = this.getStatusTooltip();
  }

  resetState() {
    this.showIcon = false;
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
    if (status.find(app => ProcessState.STOPPED_CRASHED === app.processState)) {
      this.processState = ProcessState.STOPPED_CRASHED;
      return;
    }

    // Display if at least one app is waiting
    if (status.find(app => ProcessState.CRASH_BACK_OFF === app.processState)) {
      this.processState = ProcessState.CRASH_BACK_OFF;
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
      return 'favorite_outline';
    }
    return 'favorite';
  }

  getStatusClass() {
    const styles = ['icon'];
    if (this.isRunning()) {
      styles.push('app-process-running');
    }
    if (this.isStopped()) {
      styles.push('app-process-stopped');
    }
    if (this.isCrashedWaiting()) {
      styles.push('app-process-crash-waiting');
    }
    if (this.isCrashedPermanently()) {
      styles.push('app-process-crash-permanently');
    }
    if (this.getShowOutOfSync() && !this.isMyVersion()) {
      styles.push('app-process-out-of-sync');
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

  isCrashedWaiting() {
    return this.processState === ProcessState.CRASH_BACK_OFF;
  }

  isCrashedPermanently() {
    return this.processState === ProcessState.STOPPED_CRASHED;
  }
}
