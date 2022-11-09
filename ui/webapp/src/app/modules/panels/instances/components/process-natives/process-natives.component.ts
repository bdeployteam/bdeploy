import { Component, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ProcessHandleDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataPopoverCellComponent } from 'src/app/modules/core/components/bd-data-popover-cell/bd-data-popover-cell.component';
import { ProcessDetailsService } from '../../services/process-details.service';

const nativePidColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'pid',
  name: 'PID',
  data: (r) => r.pid,
  isId: true,
  width: '80px',
};

const nativeCmdColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'cmd',
  name: 'Command',
  data: (r) => r.command,
  width: '190px',
};

/* only shows something on unix systems, windows cannot do it */
const nativeArgsColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'args',
  name: 'Arguments',
  data: (r) => r.arguments,
  showWhen: '(min-width: 1000px)',
  component: BdDataPopoverCellComponent,
};

const nativeCpuColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'cpu',
  name: 'CPU Seconds',
  data: (r) => r.totalCpuDuration,
  width: '40px',
  showWhen: '(min-width: 650px)',
};

const nativeUserColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'user',
  name: 'Native User',
  data: (r) => r.user,
  width: '170px',
  showWhen: '(min-width: 800px)',
};

const nativeTimeColumn: BdDataColumn<ProcessHandleDto> = {
  id: 'startTime',
  name: 'Started At',
  data: (r) => r.startTime,
  width: '130px',
  component: BdDataDateCellComponent,
};

@Component({
  selector: 'app-process-natives',
  templateUrl: './process-natives.component.html',
})
export class ProcessNativesComponent implements OnDestroy {
  /* template */ columns: BdDataColumn<ProcessHandleDto>[] = [
    nativePidColumn,
    nativeCmdColumn,
    nativeArgsColumn,
    nativeCpuColumn,
    nativeUserColumn,
    nativeTimeColumn,
  ];
  /* template */ processes: ProcessHandleDto[] = [];

  private subscription: Subscription;

  constructor(public details: ProcessDetailsService) {
    this.subscription = this.details.processDetail$.subscribe((detail) => {
      // FIXME: don't always reset records, this re-creates the table all the time, leading to popups closing.
      this.processes = [];
      if (detail) {
        this.flattenProcesses(this.processes, detail.handle);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private flattenProcesses(processes: any[], detail: ProcessHandleDto) {
    processes.push(detail);
    detail.children.forEach((child) => this.flattenProcesses(processes, child));
  }
}
