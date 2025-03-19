import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ProcessHandleDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataPopoverCellComponent } from 'src/app/modules/core/components/bd-data-popover-cell/bd-data-popover-cell.component';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ProcessDetailsService } from '../../services/process-details.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';

const nativePidColumn: BdDataColumn<ProcessHandleDto, number> = {
  id: 'pid',
  name: 'PID',
  data: (r) => r.pid,
  isId: true,
  width: '90px',
};

const nativeCmdColumn: BdDataColumn<ProcessHandleDto, string> = {
  id: 'cmd',
  name: 'Command',
  data: (r) => r.command,
  width: '190px',
};

/* only shows something on unix systems, windows cannot do it */
const nativeArgsColumn: BdDataColumn<ProcessHandleDto, string[]> = {
  id: 'args',
  name: 'Arguments',
  data: (r) => r.arguments,
  showWhen: '(min-width: 1000px)',
  component: BdDataPopoverCellComponent,
};

const nativeCpuColumn: BdDataColumn<ProcessHandleDto, number> = {
  id: 'cpu',
  name: 'CPU Seconds',
  data: (r) => r.totalCpuDuration,
  width: '40px',
  showWhen: '(min-width: 650px)',
};

const nativeUserColumn: BdDataColumn<ProcessHandleDto, string> = {
  id: 'user',
  name: 'Native User',
  data: (r) => r.user,
  width: '170px',
  showWhen: '(min-width: 800px)',
};

const nativeTimeColumn: BdDataColumn<ProcessHandleDto, number> = {
  id: 'startTime',
  name: 'Started At',
  data: (r) => r.startTime,
  width: '155px',
  component: BdDataDateCellComponent,
};

@Component({
    selector: 'app-process-natives',
    templateUrl: './process-natives.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent]
})
export class ProcessNativesComponent implements OnInit, OnDestroy {
  protected readonly details = inject(ProcessDetailsService);

  protected readonly columns: BdDataColumn<ProcessHandleDto, unknown>[] = [
    nativePidColumn,
    nativeCmdColumn,
    nativeArgsColumn,
    nativeCpuColumn,
    nativeUserColumn,
    nativeTimeColumn,
  ];
  protected processes: ProcessHandleDto[] = [];

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.details.processDetail$.subscribe((detail) => {
      this.processes = [];
      if (ProcessesService.isRunning(detail?.status?.processState)) {
        this.flattenProcesses(this.processes, detail.handle);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private flattenProcesses(processes: unknown[], detail: ProcessHandleDto) {
    processes.push(detail);
    detail.children.forEach((child) => this.flattenProcesses(processes, child));
  }
}
