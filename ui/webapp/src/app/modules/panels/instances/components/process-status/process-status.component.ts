import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { ApplicationStartType, ProcessDetailDto, ProcessState } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ProcessDetailsService } from '../../services/process-details.service';

@Component({
  selector: 'app-process-status',
  templateUrl: './process-status.component.html',
  styleUrls: ['./process-status.component.css'],
})
export class ProcessStatusComponent implements OnInit, OnDestroy {
  /* template */ uptime$ = new BehaviorSubject<string>(null);
  /* template */ restartProgress$ = new BehaviorSubject<number>(0);
  /* template */ restartProgressText$ = new BehaviorSubject<string>(null);
  /* template */ outdated$ = new BehaviorSubject<boolean>(false);

  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ restarting$ = new BehaviorSubject<boolean>(false);

  private performing$ = new BehaviorSubject<boolean>(false);

  private restartProgressHandle: any;
  private uptimeCalculateHandle: any;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    private auth: AuthenticationService,
    public details: ProcessDetailsService,
    public processes: ProcessesService,
    public instances: InstancesService,
    private cfg: ConfigService
  ) {}

  ngOnInit(): void {
    this.subscription = combineLatest([this.details.processDetail$, this.instances.active$]).subscribe(([detail, active]) => {
      this.clearIntervals();
      this.outdated$.next(false);

      if (!detail) {
        return;
      }

      // when switching to another process, we *need* to forget those, even if we cannot restore them later on.
      this.starting$.next(false);
      this.stopping$.next(false);
      this.restarting$.next(false);

      this.outdated$.next(detail.status.instanceTag !== active.activeVersion.tag);

      if (this.isCrashedWaiting(detail)) {
        this.restartProgressHandle = setInterval(() => this.doUpdateRestartProgress(detail), 1000);
        this.doUpdateRestartProgress(detail);
      }

      if (this.isRunning(detail)) {
        this.uptimeCalculateHandle = setTimeout(() => this.doCalculateUptimeString(detail), 1);
      }
    });

    this.subscription.add(
      combineLatest([this.starting$, this.stopping$, this.restarting$])
        .pipe(map(([a, b, c]) => a || b || c))
        .subscribe((b) => {
          this.performing$.next(b);
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.clearIntervals();
  }

  private clearIntervals() {
    if (this.restartProgressHandle) {
      clearInterval(this.restartProgressHandle);
    }
    if (this.uptimeCalculateHandle) {
      clearTimeout(this.uptimeCalculateHandle);
    }
  }

  /* template */ isRunning(detail: ProcessDetailDto) {
    return ProcessesService.isRunning(detail.status.processState);
  }

  /* template */ isCrashedWaiting(detail: ProcessDetailDto) {
    return detail.status.processState === ProcessState.CRASHED_WAITING;
  }

  /* template */ formatStartType(type: ApplicationStartType) {
    switch (type) {
      case ApplicationStartType.INSTANCE:
        return 'Instance';
      case ApplicationStartType.MANUAL:
        return 'Manual';
      case ApplicationStartType.MANUAL_CONFIRM:
        return 'Confirmed Manual';
    }
  }

  /* template */ isStartAllowed(detail: ProcessDetailDto) {
    if (!this.auth.isCurrentScopeWrite() || this.outdated$.value || this.performing$.value) {
      return false;
    }

    return !this.isRunning(detail);
  }

  /* template */ isStopAllowed(detail: ProcessDetailDto) {
    if (!this.auth.isCurrentScopeWrite() || this.performing$.value) {
      return false;
    }

    return this.isRunning(detail) || this.isCrashedWaiting(detail);
  }

  /* template */ isRestartAllowed(detail: ProcessDetailDto) {
    if (!this.auth.isCurrentScopeWrite() || this.outdated$.value || this.performing$.value) {
      return false;
    }

    return this.isRunning(detail) || this.isCrashedWaiting(detail);
  }

  /* template */ start(detail: ProcessDetailDto) {
    this.starting$.next(true);
    let confirmation = of(true);

    // rather die than "mistakingly" start a manual confirm application.
    const config = this.details.processConfig$.value;
    if (!config) {
      throw new Error('Process config not available?!');
    }

    if (config.processControl.startType === ApplicationStartType.MANUAL_CONFIRM) {
      confirmation = this.dialog.message({
        header: 'Confirm Process Start',
        message: `Please confirm the start of <strong>${config.name}</strong>.`,
        icon: 'play_arrow',
        confirmation: config.name,
        confirmationHint: 'Confirm using process name',
        actions: [ACTION_CANCEL, ACTION_OK],
      });
    }

    confirmation.subscribe((b) => {
      if (!b) {
        this.starting$.next(false);
        return;
      }
      this.processes
        .start(detail.status.appUid)
        .pipe(finalize(() => this.starting$.next(false)))
        .subscribe();
    });
  }

  /* template */ stop(detail: ProcessDetailDto) {
    this.stopping$.next(true);
    this.processes
      .stop(detail.status.appUid)
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  /* template */ restart(detail: ProcessDetailDto) {
    this.restarting$.next(true);
    this.processes
      .restart(detail.status.appUid)
      .pipe(finalize(() => this.restarting$.next(false)))
      .subscribe();
  }

  private doCalculateUptimeString(detail) {
    this.uptimeCalculateHandle = null;
    if (this.isRunning(detail)) {
      const now = this.cfg.getCorrectedNow(); // server's 'now'
      const ms = now - detail.handle.startTime; // this comes from the node. node and master are assumed to have the same time.
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
      this.uptime$.next(s);
      this.uptimeCalculateHandle = setTimeout(() => this.doCalculateUptimeString(detail), delay);
    } else {
      this.uptime$.next(null);
    }
  }

  private doUpdateRestartProgress(detail: ProcessDetailDto) {
    const diff = detail.recoverAt - this.cfg.getCorrectedNow();
    if (diff < 100) {
      this.processes.reload();
    } else {
      const totalSeconds = detail.recoverDelay + 2;
      const remainingSeconds = Math.round(diff / 1000);
      this.restartProgress$.next(100 - 100 * (remainingSeconds / totalSeconds));
      this.restartProgressText$.next(remainingSeconds + ' seconds');
    }
  }
}
