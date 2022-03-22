import { Component, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationStartType, InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesBulkService } from 'src/app/modules/primary/instances/services/processes-bulk.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-bulk-control',
  templateUrl: './bulk-control.component.html',
})
export class BulkControlComponent {
  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ restarting$ = new BehaviorSubject<boolean>(false);

  /* template */ startingMulti$ = new BehaviorSubject<boolean>(false);
  /* template */ stoppingMulti$ = new BehaviorSubject<boolean>(false);
  /* template */ restartingMulti$ = new BehaviorSubject<boolean>(false);

  /* template */ bulkContainsConfirmed = false;
  /* template */ bulkSelection: string[] = [];

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public instances: InstancesService,
    public servers: ServersService,
    private processes: ProcessesService,
    public bulk: ProcessesBulkService
  ) {
    this.bulk.selection$.subscribe((s) => {
      if (!s) {
        this.bulkContainsConfirmed = false;
        this.bulkSelection = [];
        return;
      }

      this.bulkContainsConfirmed = Object.keys(s).some(
        (k) =>
          s[k].filter(
            (c) =>
              c?.processControl?.startType ===
              ApplicationStartType.MANUAL_CONFIRM
          ).length > 0
      );

      const result: string[] = [];
      Object.keys(s).forEach((k) => {
        result.push(...s[k].map((c) => c.uid));
      });
      this.bulkSelection = result;
    });
  }

  /* template */ doStart(instance: InstanceDto) {
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

  /* template */ doStop(instance: InstanceDto) {
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

  /* template */ doRestart(instance: InstanceDto) {
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

  /* template */ doStartMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Start',
        `This will start selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.startingMulti$.next(true);
          this.processes
            .start(this.bulkSelection)
            .pipe(finalize(() => this.startingMulti$.next(false)))
            .subscribe();
        }
      });
  }

  /* template */ doStopMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Stop',
        `This will stop selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.stoppingMulti$.next(true);
          this.processes
            .stop(this.bulkSelection)
            .pipe(finalize(() => this.stoppingMulti$.next(false)))
            .subscribe();
        }
      });
  }

  /* template */ doRestartMulti(instance: InstanceDto) {
    this.dialog
      .confirm(
        'Confirm Restart',
        `This will restart selected processes in the instance <strong>${instance.instanceConfiguration.name}</strong>. Do you want to continue?`
      )
      .subscribe((b) => {
        if (b) {
          this.restartingMulti$.next(true);
          this.processes
            .restart(this.bulkSelection)
            .pipe(finalize(() => this.restartingMulti$.next(false)))
            .subscribe();
        }
      });
  }
}
