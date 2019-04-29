import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { MatBottomSheet, MatBottomSheetRef } from '@angular/material';
import { distanceInWordsStrict, format } from 'date-fns';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationConfiguration, ProcessDetailDto, ProcessState, ProcessStatusDto } from '../models/gen.dtos';
import { ProcessListComponent } from '../process-list/process-list.component';
import { ProcessService } from '../services/process.service';
import { unsubscribe } from '../utils/object.utils';

@Component({
  selector: 'app-process-details',
  templateUrl: './process-details.component.html',
  styleUrls: ['./process-details.component.css'],
})
export class ProcessDetailsComponent implements OnInit, OnChanges, OnDestroy {
  @Input() instanceGroup: string;
  @Input() instanceId: string;
  @Input() instanceTag: string;
  @Input() activatedInstanceTag: string;
  @Input() appConfig: ApplicationConfiguration;

  status: ProcessStatusDto;
  subscription: Subscription;

  loading = true;
  restartProgress: number;
  restartProgressText: string;
  restartProgressHandle: any;

  processSheet: MatBottomSheetRef<ProcessListComponent>;

  constructor(private processService: ProcessService, private bottomSheet: MatBottomSheet) {}

  ngOnInit() {
    this.subscription = this.processService.subscribe(() => this.onStatusChanged());
    this.ngOnChanges();
  }

  ngOnChanges() {
    /** Get initial state from service */
    this.onStatusChanged();

    /** Trigger immediate update  */
    this.reLoadStatus();
  }

  ngOnDestroy() {
    unsubscribe(this.subscription);
    if (this.restartProgressHandle) {
      clearInterval(this.restartProgressHandle);
    }
  }

  /** Called when the status of the process changed */
  onStatusChanged() {
    this.status = this.processService.getStatusOfApp(this.appConfig.uid);
    this.loading = false;

    // Clear interval handle
    if (this.restartProgressHandle) {
      clearInterval(this.restartProgressHandle);
    }

    // Schedule update countdown
    if (this.isStoppedWaiting()) {
      this.restartProgressHandle = setInterval(() => this.doUpdateRestartProgress(), 1000);
      this.doUpdateRestartProgress();
    }

    // Update sheet when open
    if (this.processSheet && this.processSheet.instance) {
      this.processSheet.instance.setStatus(this.status);
    }
  }

  reLoadStatus() {
    this.loading = true;
    this.processService.refreshStatus(this.instanceGroup, this.instanceId);
  }

  start() {
    this.loading = true;
    this.processService
      .startProcess(this.instanceGroup, this.instanceId, this.appConfig.uid)
      .pipe(finalize(() => this.reLoadStatus()))
      .subscribe(r => {});
  }

  stop() {
    this.loading = true;
    this.processService
      .stopProcess(this.instanceGroup, this.instanceId, this.appConfig.uid)
      .pipe(finalize(() => this.reLoadStatus()))
      .subscribe(r => {});
  }

  restart() {
    this.loading = true;
    this.processService
      .restartProcess(this.instanceGroup, this.instanceId, this.appConfig.uid)
      .pipe(finalize(() => this.reLoadStatus()))
      .subscribe(r => {});
  }

  /** Returns whether or not the process is running */
  isRunning() {
    return this.hasState(ProcessState.RUNNING);
  }

  /** Returns whether or not the process is running unstable */
  isRunningUnstable() {
    return this.hasState(ProcessState.RUNNING_UNSTABLE);
  }

  /** Returns whether or not the process is running or running unstable */
  isRunningOrUnstable() {
    return this.isRunning() || this.isRunningUnstable();
  }

  /** Returns whether or not this tag represents the active one */
  isActivated() {
    return this.instanceTag === this.activatedInstanceTag;
  }

  isMyVersion() {
    return this.status.instanceTag === this.instanceTag;
  }

  /** Returns whether or not the status of the process corresponds to the activated one */
  isOutOfSync() {
    return this.status.instanceTag !== this.activatedInstanceTag;
  }

  /** Returns whether or not the process is running in a not-activated version */
  isRunningOutOfSync() {
    const state = this.status.processState;
    const desired = new Set();
    desired.add(ProcessState.RUNNING);
    desired.add(ProcessState.RUNNING_UNSTABLE);
    desired.add(ProcessState.CRASH_BACK_OFF);
    if (!desired.has(state)) {
      return false;
    }
    return this.isOutOfSync();
  }

  /** Returns whether or the process is stopped in this version */
  isStopped() {
    return this.hasState(ProcessState.STOPPED) && this.isMyVersion();
  }

  /** Returns whether or the process is stopped crashed in this version */
  isStoppedPermanently() {
    return this.hasState(ProcessState.STOPPED_CRASHED) && this.isMyVersion();
  }

  /** Returns whether or the process is stopped waiting in this version */
  isStoppedWaiting() {
    return this.hasState(ProcessState.CRASH_BACK_OFF) && this.isMyVersion();
  }

  canStart() {
    return (this.isStopped() || this.isStoppedPermanently() || this.isStoppedWaiting()) && this.isMyVersion();
  }

  canStop() {
    return this.isRunning() || this.isRunningUnstable() || this.isStoppedWaiting();
  }

  canRestart() {
    return (this.isRunning() || this.isRunningUnstable()) && this.isActivated() && this.isMyVersion();
  }

  getStartTime() {
    return format(new Date(this.status.processDetails.startTime), 'DD.MM.YYYY HH:mm');
  }

  getStopTime() {
    if (this.status.stopTime === -1) {
      return '-';
    }
    return format(new Date(this.status.stopTime), 'DD.MM.YYYY HH:mm');
  }

  getRestartTime() {
    return format(new Date(this.status.recoverAt), 'DD.MM.YYYY HH:mm');
  }

  getUpTimeInWords() {
    return distanceInWordsStrict(Date.now(), this.status.processDetails.startTime, {
      unit: 'm' || 'h' || 'd' || 'M' || 'Y',
      partialMethod: 'ceil',
    });
  }

  getProcessCount() {
    if (!this.status || !this.status.processDetails) {
      return 0;
    }
    return this.countProcessRecursive(this.status.processDetails);
  }

  getStatusDetails() {
    if (this.isRunningOutOfSync()) {
      return 'Application is running in a version that is not activated. Only stopping is possible.';
    }
    if (!this.isMyVersion() && this.canStop()) {
      return 'Application is running in another version. Only stopping is possible.';
    }
    if (!this.isActivated()) {
      return 'Process control is disabled because this version is not activated.';
    }
    if (this.isStoppedPermanently()) {
      return 'The application crashed repeatedly. The process control gave up restarting it.';
    }
    if (this.isStoppedWaiting()) {
      return 'The application terminated unexpectedly. The process control is delaying restart for a short time to reduce load on the node.';
    }
    return null;
  }

  hasState(desired: ProcessState) {
    if (!this.status) {
      return false;
    }
    return this.status.processState === desired;
  }

  doUpdateRestartProgress() {
    const diff = this.status.recoverAt - Date.now();
    if (diff < 100) {
      this.reLoadStatus();
    } else {
      const totalSeconds = this.status.recoverDelay + 2;
      const remainingSeconds = Math.round(diff / 1000);
      this.restartProgress = 100 - 100 * (remainingSeconds / totalSeconds);
      this.restartProgressText = remainingSeconds + ' seconds';
    }
  }

  showProcessList() {
    this.processSheet = this.bottomSheet.open(ProcessListComponent, {
      data: {
        statusDto: this.status,
        appConfig: this.appConfig,
      },
    });
  }

  countProcessRecursive(parent: ProcessDetailDto): number {
    let number = 1;
    parent.children.forEach(child => {
      number += this.countProcessRecursive(child);
    });
    return number;
  }
}
