import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessOutdatedComponent } from '../components/dashboard/process-outdated/process-outdated.component';
import { ProcessStatusIconComponent } from '../components/dashboard/process-status-icon/process-status-icon.component';
import { PortStatusColumnComponent } from '../components/port-status-column/port-status-column.component';
import { ProcessNameAndOsComponent } from '../components/process-name-and-os/process-name-and-os.component';
import { InstanceEditService, ProcessEditState } from './instance-edit.service';
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

  processNameAndOsColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'nameAndOs',
    name: 'Configuration Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    component: ProcessNameAndOsComponent,
  };

  processIdColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => (!!r.uid ? r.uid : 'New Process'),
    width: '120px',
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

  processNameAndEditStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Configuration Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    classes: (r) => this.getStateClass(r),
  };

  processNameAndOsAndEditStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Configuration Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    classes: (r) => this.getStateClass(r),
    component: ProcessNameAndOsComponent,
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
    width: '200px',
    showWhen: '(min-width: 700px)',
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
    component: PortStatusColumnComponent,
    data: (r) => this.getAllPortsRating(r),
    width: '64px',
  };

  defaultProcessesColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.processStatusColumn,
    this.processPortRatingColumn,
    this.processActualityColumn,
  ];

  defaultProcessClientColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameAndOsColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
  ];

  defaultProcessesConfigColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameAndEditStatusColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
  ];

  defaultProcessesConfigClientColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameAndOsAndEditStatusColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
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

  private getStateClass(r: ApplicationConfiguration) {
    switch (this.edit.getProcessEditState(r.uid)) {
      case ProcessEditState.ADDED:
        return ['bd-status-border-added'];
      case ProcessEditState.INVALID:
        return ['bd-status-border-invalid'];
      case ProcessEditState.CHANGED:
        return ['bd-status-border-changed'];
    }
    return ['bd-status-border-none'];
  }
}
