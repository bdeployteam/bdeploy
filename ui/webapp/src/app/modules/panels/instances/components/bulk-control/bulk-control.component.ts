import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Actions, ApplicationStartType, InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesBulkService } from 'src/app/modules/primary/instances/services/processes-bulk.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdNotificationCardComponent } from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-bulk-control',
  templateUrl: './bulk-control.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdNotificationCardComponent,
    BdButtonComponent,
    MatDivider,
    AsyncPipe,
  ],
})
export class BulkControlComponent implements OnInit {
  private readonly processes = inject(ProcessesService);
  private readonly actions = inject(ActionsService);
  protected readonly instances = inject(InstancesService);
  protected readonly servers = inject(ServersService);
  protected readonly bulk = inject(ProcessesBulkService);

  private readonly starting$ = new BehaviorSubject<boolean>(false);
  private readonly stopping$ = new BehaviorSubject<boolean>(false);
  private readonly restarting$ = new BehaviorSubject<boolean>(false);

  private readonly startingMulti$ = new BehaviorSubject<boolean>(false);
  private readonly stoppingMulti$ = new BehaviorSubject<boolean>(false);
  private readonly restartingMulti$ = new BehaviorSubject<boolean>(false);

  protected bulkContainsConfirmed = false;
  protected bulkSelection$ = new BehaviorSubject<string[]>(null);

  protected mappedStart$ = this.actions.action([Actions.START_INSTANCE], this.starting$);
  protected mappedStop$ = this.actions.action([Actions.STOP_INSTANCE], this.starting$);
  protected mappedRestart$ = this.actions.action([Actions.START_INSTANCE, Actions.STOP_INSTANCE], this.restarting$);

  protected mappedStartMulti$ = this.actions.action(
    [Actions.START_PROCESS],
    this.startingMulti$,
    null,
    null,
    this.bulkSelection$
  );
  protected mappedStopMulti$ = this.actions.action(
    [Actions.STOP_PROCESS],
    this.stoppingMulti$,
    null,
    null,
    this.bulkSelection$
  );
  protected mappedRestartMulti$ = this.actions.action(
    [Actions.START_PROCESS, Actions.STOP_PROCESS],
    this.restartingMulti$,
    null,
    null,
    this.bulkSelection$
  );

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit(): void {
    this.bulk.selection$.subscribe((s) => {
      if (!s) {
        this.bulkContainsConfirmed = false;
        this.bulkSelection$.next([]);
        return;
      }

      this.bulkContainsConfirmed = Object.keys(s).some((k) =>
        s[k].some((c) => c?.processControl?.startType === ApplicationStartType.MANUAL_CONFIRM)
      );

      const result: string[] = [];
      Object.keys(s).forEach((k) => {
        result.push(...s[k].map((c) => c.id));
      });
      this.bulkSelection$.next(result);
    });
  }

  protected doStart(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Start',
        `This will start all processes of start type 'Instance' in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.starting$.next(true);
          this.processes
            .startInstance()
            .pipe(finalize(() => this.starting$.next(false)))
            .subscribe();
        }
      });
  }

  protected doStop(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Stop',
        `This will stop all currently running processes of start type 'Instance' in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.stopping$.next(true);
          this.processes
            .stopInstance()
            .pipe(finalize(() => this.stopping$.next(false)))
            .subscribe();
        }
      });
  }

  protected doRestart(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Restart',
        `This will restart all running, and start all other processes of start type 'Instance' in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.restarting$.next(true);
          this.processes
            .restartInstance()
            .pipe(finalize(() => this.restarting$.next(false)))
            .subscribe();
        }
      });
  }

  protected doStartMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Start',
        `This will start selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.startingMulti$.next(true);
          this.processes
            .start(this.bulkSelection$.value)
            .pipe(finalize(() => this.startingMulti$.next(false)))
            .subscribe();
        }
      });
  }

  protected doStopMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Stop',
        `This will stop selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.stoppingMulti$.next(true);
          this.processes
            .stop(this.bulkSelection$.value)
            .pipe(finalize(() => this.stoppingMulti$.next(false)))
            .subscribe();
        }
      });
  }

  protected doRestartMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Restart',
        `This will restart selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.restartingMulti$.next(true);
          this.processes
            .restart(this.bulkSelection$.value)
            .pipe(finalize(() => this.restartingMulti$.next(false)))
            .subscribe();
        }
      });
  }
}
