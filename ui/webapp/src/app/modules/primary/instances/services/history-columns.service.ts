import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { HistoryEntryDto, HistoryEntryType, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { InstanceStateService } from './instance-state.service';
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
    data: (r) => this.getStateIcon(r),
    width: '64px',
    component: BdDataIconCellComponent,
    classes: (r) => this.getStateClass(r),
  };

  public historyVersionColumn: BdDataColumn<HistoryEntryDto> = {
    id: 'version',
    name: 'Version',
    data: (r) => this.getVersionText(r),
    width: '64px',
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

  private states: InstanceStateRecord;

  constructor(private instances: InstancesService, private state: InstanceStateService) {
    this.state.state$.subscribe((s) => (this.states = s));
  }

  private getVersionText(row: HistoryEntryDto) {
    if (row.instanceTag === this.instances.current$.value?.activeVersion?.tag) {
      return `${row.instanceTag} - Active`;
    }

    if (row.instanceTag === this.instances.current$.value?.instance?.tag) {
      return `${row.instanceTag} - Current`;
    }

    return row.instanceTag;
  }

  private getStateIcon(row: HistoryEntryDto) {
    if (this.states?.activeTag === row.instanceTag) {
      return 'check_circle'; // active
    } else if (!!this.states?.installedTags?.find((v) => v === row.instanceTag)) {
      return 'check_circle_outline'; // installed
    }

    return null;
  }

  private getStateClass(row: HistoryEntryDto): string[] {
    if (this.states?.activeTag === row.instanceTag) {
      return [];
    }

    if (!!this.states?.installedTags?.find((v) => v === row.instanceTag)) {
      // if the version is older than the last-active tag, we'll uninstall it later on.
      if (!!this.states?.lastActiveTag) {
        if (Number(this.states.lastActiveTag) > Number(row.instanceTag)) {
          return ['bd-description-text'];
        }
      }
    }
    return [];
  }
}
