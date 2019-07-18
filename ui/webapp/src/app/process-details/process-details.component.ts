import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, OnChanges, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { MatBottomSheet, MatBottomSheetRef, MatDialog } from '@angular/material';
import { format } from 'date-fns';
import { Observable, Subscription } from 'rxjs';
import { finalize, map, mergeMap } from 'rxjs/operators';
import { ApplicationConfiguration, ApplicationStartType, InstanceDirectoryEntry, ProcessDetailDto, ProcessState, ProcessStatusDto, StringEntryChunkDto } from '../models/gen.dtos';
import { ProcessListComponent } from '../process-list/process-list.component';
import { ProcessStartConfirmComponent } from '../process-start-confirm/process-start-confirm.component';
import { InstanceService } from '../services/instance.service';
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

  uptimeString = '';
  uptimeCalculateHandle: any;

  processSheet: MatBottomSheetRef<ProcessListComponent>;

  private overlayRef: OverlayRef;

  constructor(private overlay: Overlay,
    private viewContainerRef: ViewContainerRef, private processService: ProcessService, private instanceService: InstanceService, private bottomSheet: MatBottomSheet, private dialog: MatDialog) {}

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
    if (this.uptimeCalculateHandle) {
      clearTimeout(this.uptimeCalculateHandle);
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
    if (this.isCrashedWaiting()) {
      this.restartProgressHandle = setInterval(() => this.doUpdateRestartProgress(), 1000);
      this.doUpdateRestartProgress();
    }

    // Clear uptimeString handle
    if (this.uptimeCalculateHandle) {
      clearTimeout(this.uptimeCalculateHandle);
    }

    if (this.isRunningOrUnstable()) {
      this.uptimeCalculateHandle = setTimeout(() => this.calculateUptimeString(), 1);
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

  async start() {
    const confirmed = await this.confirmStart();
    if (!confirmed) {
      return;
    }
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

  /** Asks the user if launching is OK if manual confirmation is required */
  async confirmStart() {
    // Check if manual confirmation is required
    if (this.appConfig.processControl.startType !== ApplicationStartType.MANUAL_CONFIRM) {
      return true;
    }

    // Ask for confirmation
    const myDialog = this.dialog.open(ProcessStartConfirmComponent, {
      data: {
        application: this.appConfig.name,
      },
    });
    const result = await myDialog.afterClosed().toPromise();
    if (result === 'start') {
      return true;
    }
    return false;
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
    desired.add(ProcessState.CRASHED_WAITING);
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
  isCrashedPermanently() {
    return this.hasState(ProcessState.CRASHED_PERMANENTLY) && this.isMyVersion();
  }

  /** Returns whether or the process is stopped waiting in this version */
  isCrashedWaiting() {
    return this.hasState(ProcessState.CRASHED_WAITING) && this.isMyVersion();
  }

  canStart() {
    return (this.isStopped() || this.isCrashedPermanently() || this.isCrashedWaiting()) && this.isMyVersion();
  }

  canStop() {
    return this.isRunning() || this.isRunningUnstable() || this.isCrashedWaiting();
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

  private calculateUptimeString() {
    this.uptimeCalculateHandle = null;
    if (this.isRunningOrUnstable()) {
      const now = Date.now();
      const ms = now - this.status.processDetails.startTime;
      const sec = Math.floor(ms / 1000) % 60;
      const min = Math.floor(ms / 60000) % 60;
      const hours = Math.floor(ms / 3600000) % 24;
      const days = Math.floor(ms / 86400000);

      let s = '';
      if (days > 0) {
        s = s + days + (days === 1 ? ' day ' : ' days ');
      }
      if (hours > 0 || days > 0) {
        s = s + hours + (hours === 1 ? ' hour ' : ' hours ');
      }
      if (min > 0 || hours > 0 || days > 0) {
        s = s + min + (min === 1 ? ' minute' : ' minutes');
      }
      let delay = 0;
      if (days === 0 && hours === 0 && min === 0) {
        s = s + sec + (sec === 1 ? ' second' : ' seconds');
        // calculate reschedule for next second
        delay = 1000 - (ms - Math.floor(ms / 1000) * 1000);
      } else {
        // calculate reschedule for next minute
        delay = 60000 - (ms - Math.floor(ms / 60000) * 60000);
      }
      this.uptimeString = s;
      this.uptimeCalculateHandle = setTimeout(() => this.calculateUptimeString(), delay);
    } else {
      this.uptimeString = '';
    }
  }

  getStartTypeText() {
    switch (this.appConfig.processControl.startType) {
      case ApplicationStartType.INSTANCE:
        return 'Instance';
      case ApplicationStartType.MANUAL:
        return 'Manual';
      case ApplicationStartType.MANUAL_CONFIRM:
        return 'Manual Confirm';
    }
  }

  getStartTypeHint() {
    switch (this.appConfig.processControl.startType) {
      case ApplicationStartType.INSTANCE:
        return 'Application will be started on node startup if "Auto Start" is configured on the instance.';
      case ApplicationStartType.MANUAL:
        return 'Application must be manually started.';
      case ApplicationStartType.MANUAL_CONFIRM:
        return 'Application must be manually started. Additional confirmation is required.';
    }
  }

  getKeepAliveText() {
    if (this.appConfig.processControl.keepAlive) {
      return 'Enabled';
    }
    return 'Disabled';
  }

  getKeepAliveHint() {
    if (this.appConfig.processControl.keepAlive) {
      return 'Application will automatically be restarted after it terminated unexpectedly.';
    }
    return 'Application remains stopped after it terminated.';
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
    if (this.isCrashedPermanently()) {
      return 'The application crashed repeatedly. The process control gave up restarting it.';
    }
    if (this.isCrashedWaiting()) {
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

  getCurrentOutputEntryFetcher(): () => Observable<InstanceDirectoryEntry> {
    const tag: string = this.status ? this.status.instanceTag : (this.activatedInstanceTag ? this.activatedInstanceTag : this.instanceTag);
    return () => this.instanceService.getApplicationOutputEntry(this.instanceGroup, this.instanceId, tag, this.appConfig.uid, false).pipe(
      map(dir => {
        if (!dir.entries || !dir.entries.length) {
          return null;
        }

        return dir.entries[0];
      })
    );
  }

  getOutputContentFetcher(): (offset: number, limit: number) => Observable<StringEntryChunkDto> {
    return (offset, limit) => {
      const tag: string = this.status ? this.status.instanceTag : (this.activatedInstanceTag ? this.activatedInstanceTag : this.instanceTag);
      return this.instanceService.getApplicationOutputEntry(this.instanceGroup, this.instanceId, tag, this.appConfig.uid, true).pipe(
        mergeMap(dir => this.instanceService.getContentChunk(this.instanceGroup, this.instanceId, dir, dir.entries[0], offset, limit, true))
      );
    };
  }

  /** Opens a modal overlay popup showing the given template */
  openOutputOverlay(template: TemplateRef<any>) {
    this.closeOutputOverlay();

    this.overlayRef = this.overlay.create({
      height: '90%',
      width: '90%',
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOutputOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  /** Closes the overlay if present */
  closeOutputOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }
}
