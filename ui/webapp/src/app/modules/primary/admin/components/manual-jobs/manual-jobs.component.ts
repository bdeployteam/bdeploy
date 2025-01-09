import { Component, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { BehaviorSubject, Subscription, map, switchMap, timer } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { JobDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { timeAgo } from 'src/app/modules/core/utils/time.utils';
import { JobService } from '../../services/jobs.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

const colName: BdDataColumn<JobDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.description,
  width: '200px',
};

const colGroup: BdDataColumn<JobDto> = {
  id: 'group',
  name: 'Group',
  data: (r) => r.group,
  width: '150px',
};

const colIsRunning: BdDataColumn<JobDto> = {
  id: 'isRunning',
  name: 'Is Running',
  data: (r) => (r.isRunning ? 'play_arrow' : 'stop'),
  width: '150px',
  component: BdDataIconCellComponent,
};

const colLastRunTime: BdDataColumn<JobDto> = {
  id: 'lastRunTime',
  name: 'Last Run Time',
  data: (r) => timeAgo(r.lastRunTime),
  width: '155px',
};

const colNextRunTime: BdDataColumn<JobDto> = {
  id: 'nextRunTime',
  name: 'Next Run Time',
  data: (r) => r.nextRunTime,
  width: '155px',
  component: BdDataDateCellComponent,
};

@Component({
    selector: 'app-manual-jobs',
    templateUrl: './manual-jobs.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, BdDataTableComponent, AsyncPipe]
})
export class ManualJobsComponent implements OnInit, OnDestroy {
  private readonly colRun: BdDataColumn<JobDto> = {
    id: 'run',
    name: 'Run',
    data: (r) => `Run ${r.name} Immediately`,
    action: (r) => this.run(r),
    actionDisabled: (r) => r.isRunning,
    icon: () => 'play_arrow',
    width: '50px',
  };

  private readonly jobService = inject(JobService);
  protected readonly columns: BdDataColumn<JobDto>[] = [
    colName,
    colGroup,
    colIsRunning,
    colLastRunTime,
    colNextRunTime,
    this.colRun,
  ];
  protected records$ = new BehaviorSubject<JobDto[]>(null);
  private subscription: Subscription;

  ngOnInit() {
    // while any job is running, reload every 5 seconds, otherwise every 30 seconds.
    this.subscription = this.records$
      .pipe(
        map((r) => r?.some((x) => x.isRunning)),
        switchMap((r) => (r ? timer(0, 5000) : timer(0, 30000))),
      )
      .subscribe((x) => {
        if (x) {
          this.load();
        }
      });

    this.load();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  load() {
    this.jobService.load().subscribe((jobs) => this.records$.next(jobs));
  }

  run(job: JobDto) {
    this.jobService.run(job).subscribe(() => this.load());
  }
}
