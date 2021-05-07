import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { BdDataBooleanCellComponent } from 'src/app/modules/core/components/bd-data-boolean-cell/bd-data-boolean-cell.component';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessOutdatedComponent } from '../components/dashboard/process-outdated/process-outdated.component';
import { ProcessStatusIconComponent } from '../components/dashboard/process-status-icon/process-status-icon.component';
import { InstanceEditService } from './instance-edit.service';
import { InstancesService } from './instances.service';
import { PortsService } from './ports.service';
import { ProcessesService } from './processes.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesColumnsService {
  processNameColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Configuration Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
  };

  processIdColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => (!!r.uid ? r.uid : 'New Process'),
    width: '100px',
    showWhen: '(min-width:1000px)',
    classes: (r) => (!!r.uid ? [] : ['bd-description-text']),
  };

  processAvatarColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'os-avatar',
    name: 'OS',
    hint: BdDataColumnTypeHint.AVATAR,
    data: (r) => `/assets/${getAppOs(r.application).toLowerCase()}.svg`,
    display: BdDataColumnDisplay.CARD,
  };

  processOsColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'os',
    name: 'OS',
    data: (r) => getAppOs(r.application),
    component: BdDataSvgIconCellComponent,
    width: '30px',
  };

  applicationNameColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'appName',
    name: 'Application Type',
    data: (r) => this.edit.getApplicationDescriptor(r.application.name)?.name,
  };

  applicationVersionColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'version',
    name: 'Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => r.application.tag,
    icon: (r) => 'system_update',
    showWhen: '(min-width:750px)',
  };

  processActualityColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'actuality',
    name: 'Actuality',
    description: 'Whether the process is running from the currently active instance version',
    hint: BdDataColumnTypeHint.DETAILS,
    component: ProcessOutdatedComponent,
    data: (r) => {
      const procTag = ProcessesService.get(this.processes.processStates$.value, r.uid)?.instanceTag;
      const instTag = this.instances.active$.value?.instance?.tag;
      return procTag === instTag ? null : procTag;
    }, // for sorting and display on the card.
    icon: (r) => 'thermostat',
    width: '70px',
  };

  processStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'status',
    name: 'Status',
    hint: BdDataColumnTypeHint.STATUS,
    component: ProcessStatusIconComponent,
    data: (r) => ProcessesService.get(this.processes.processStates$.value, r.uid)?.processState,
    width: '64px',
  };

  processPortRatingColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'portStates',
    name: 'Ports',
    hint: BdDataColumnTypeHint.STATUS,
    component: BdDataBooleanCellComponent,
    data: (r) => this.getAllPortsRating(r),
    width: '64px',
  };

  defaultProcessesColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationVersionColumn,
    this.processStatusColumn,
    this.processPortRatingColumn,
    this.processActualityColumn,
  ];

  defaultProcessesConfigColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.applicationVersionColumn,
  ];

  defaultProcessesConfigClientColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processOsColumn,
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.applicationVersionColumn,
  ];

  constructor(private processes: ProcessesService, private instances: InstancesService, private ports: PortsService, private edit: InstanceEditService) {}

  private getAllPortsRating(r: ApplicationConfiguration) {
    const currentStates = this.ports.activePortStates$.value;
    const processState = ProcessesService.get(this.processes.processStates$.value, r.uid);
    if (!currentStates || !processState) {
      return undefined;
    }

    const appPorts = currentStates.filter((p) => p.appUid === r.uid);
    if (ProcessesService.isRunning(processState.processState)) {
      // process running, all ports should be open.
      return appPorts.every((p) => p.state);
    } else {
      // process not running, all ports should be closed.
      return appPorts.every((p) => !p.state);
    }
  }
}
