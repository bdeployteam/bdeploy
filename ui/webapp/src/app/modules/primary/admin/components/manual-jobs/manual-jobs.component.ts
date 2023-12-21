import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { JobDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { JobService } from '../../services/jobs.service';

const colName: BdDataColumn<JobDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '150px',
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
  data: (r) => r.isRunning,
  width: '150px',
};

const colLastRunTime: BdDataColumn<JobDto> = {
  id: 'lastRunTime',
  name: 'Last Run Time',
  data: (r) => r.lastRunTime,
  width: '155px',
  component: BdDataDateCellComponent,
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
})
export class ManualJobsComponent implements OnInit {
  private readonly colRun: BdDataColumn<JobDto> = {
    id: 'run',
    name: 'Run',
    data: (r) => `Run ${r.name} Immediately`,
    action: (r) => this.run(r),
    icon: () => 'arrow_right',
    width: '50px',
  };

  private jobService = inject(JobService);
  protected columns: BdDataColumn<JobDto>[] = [
    colName,
    colGroup,
    colIsRunning,
    colLastRunTime,
    colNextRunTime,
    this.colRun,
  ];
  protected records$ = new BehaviorSubject<JobDto[]>(null);

  ngOnInit() {
    this.load();
  }

  load() {
    this.jobService.load().subscribe((jobs) => this.records$.next(jobs));
  }

  run(job: JobDto) {
    this.jobService.run(job).subscribe(() => this.load());
  }
}
