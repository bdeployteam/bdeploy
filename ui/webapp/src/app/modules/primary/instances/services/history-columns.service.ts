import { Injectable } from '@angular/core';
import { format } from 'date-fns';
import { BdDataColumn } from 'src/app/models/data';
import { HistoryEntryDto, HistoryEntryType } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { ProcessesService } from './processes.service';

const historyTimestampColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'timestamp',
  name: 'Date/Time',
  data: (r) => format(r.timestamp, 'dd.MM.yyy HH:mm:ss'),
  width: '135px',
  showWhen: '(min-width: 900px)',
};

const historyUserColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'user',
  name: 'User',
  data: (r) => r.user,
  width: '25%',
};

const historyTypeColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'type',
  name: 'Type',
  data: (r) => {
    switch (r.type) {
      case HistoryEntryType.CREATE:
        return 'add_circle_outline';
      case HistoryEntryType.DEPLOYMENT:
        return 'downloading';
      case HistoryEntryType.RUNTIME:
        return 'run_circle';
    }
  },
  classes: (r) => {
    switch (r.type) {
      case HistoryEntryType.CREATE:
        return ['bd-success-text'];
      case HistoryEntryType.DEPLOYMENT:
        return ['bd-accent-text'];
      case HistoryEntryType.RUNTIME:
        return ['bd-warning-text'];
    }
  },
  component: BdDataIconCellComponent,
  width: '40px',
};

const historyTitleColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'title',
  name: 'Event',
  data: (r) => r.title,
};

const historyPidColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'pid',
  name: 'PID',
  data: (r) => (!!r.runtimeEvent && !!r.runtimeEvent.pid ? r.runtimeEvent.pid : null),
  width: '64px',
  showWhen: '(min-width: 950px)',
};

const historyExitCodeColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'exitCode',
  name: 'Exit Code',
  data: (r) => (!!r.runtimeEvent && !ProcessesService.isRunning(r.runtimeEvent.state) ? r.runtimeEvent.exitCode : null),
  width: '64px',
  showWhen: '(min-width: 1000px)',
};

const historyVersionColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'version',
  name: 'Version',
  data: (r) => r.instanceTag,
  width: '50px',
};

@Injectable({
  providedIn: 'root',
})
export class HistoryColumnsService {
  public defaultHistoryColumns: BdDataColumn<HistoryEntryDto>[] = [
    historyTimestampColumn,
    historyUserColumn,
    historyTypeColumn,
    historyTitleColumn,
    historyPidColumn,
    historyExitCodeColumn,
    historyVersionColumn,
  ];

  constructor() {}
}
