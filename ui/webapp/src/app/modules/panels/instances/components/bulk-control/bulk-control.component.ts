import { Component, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
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

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public instances: InstancesService,
    public servers: ServersService,
    private processes: ProcessesService
  ) {}

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
}
