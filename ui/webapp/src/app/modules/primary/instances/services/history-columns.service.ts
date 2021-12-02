import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { HistoryEntryDto, HistoryEntryType } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { HistoryStateColumnComponent } from '../components/history-state-column/history-state-column.component';
import { InstancesService } from './instances.service';

const historyTimestampColumn: BdDataColumn<HistoryEntryDto> = {
  id: 'timestamp',
  name: 'Date/Time',
  data: (r) => r.timestamp,
  width: '135px',
  showWhen: '(min-width: 900px)',
  component: BdDataDateCellComponent,
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
  showWhen: '(min-width: 1200px)',
};

@Injectable({
  providedIn: 'root',
})
export class HistoryColumnsService {
  public historyStateColumn: BdDataColumn<HistoryEntryDto> = {
    id: 'state',
    name: 'State',
    data: (r) => r.instanceTag,
    width: '64px',
    component: HistoryStateColumnComponent,
  };

  public historyVersionColumn: BdDataColumn<HistoryEntryDto> = {
    id: 'version',
    name: 'Version',
    data: (r) => this.getVersionText(r),
    width: '100px',
  };

  public defaultHistoryColumns: BdDataColumn<HistoryEntryDto>[] = [
    historyTimestampColumn,
    historyUserColumn,
    historyTypeColumn,
    historyTitleColumn,
    historyPidColumn,
    this.historyStateColumn,
    this.historyVersionColumn,
  ];

  constructor(private instances: InstancesService) {}

  private getVersionText(row: HistoryEntryDto) {
    if (row.instanceTag === this.instances.current$.value?.activeVersion?.tag) {
      return `${row.instanceTag} - Active`;
    }

    if (row.instanceTag === this.instances.current$.value?.instance?.tag) {
      return `${row.instanceTag} - Current`;
    }

    return row.instanceTag;
  }
}
