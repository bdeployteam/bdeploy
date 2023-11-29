import { Injectable, inject } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { BdIdentifierCellComponent } from 'src/app/modules/core/components/bd-identifier-cell/bd-identifier-cell.component';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessOutdatedComponent } from '../components/dashboard/process-outdated/process-outdated.component';
import { ProcessStatusIconComponent } from '../components/dashboard/process-status-icon/process-status-icon.component';
import { PortStatusColumnComponent } from '../components/port-status-column/port-status-column.component';
import { ProcessNameAndOsComponent } from '../components/process-name-and-os/process-name-and-os.component';
import { InstancesService } from './instances.service';
import { PortsService } from './ports.service';
import { ProcessesService } from './processes.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesColumnsService {
  private processes = inject(ProcessesService);
  private instances = inject(InstancesService);
  private ports = inject(PortsService);

  private processNameColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
  };

  private processNameAndOsColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'nameAndOs',
    name: 'Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    component: ProcessNameAndOsComponent,
  };

  private processIdColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => (r.id ? r.id : 'new'),
    isId: true,
    width: '122px',
    showWhen: '(min-width:1000px)',
    component: BdIdentifierCellComponent,
  };

  private processAvatarColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'os-avatar',
    name: 'OS',
    hint: BdDataColumnTypeHint.AVATAR,
    data: (r) => `/assets/${getAppOs(r.application).toLowerCase()}.svg`,
    display: BdDataColumnDisplay.CARD,
  };

  public applicationNameColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'appName',
    name: 'Application Type',
    data: (r) =>
      this.instances.activeNodeCfgs$.value?.applications?.find((app) => app.key.name === r.application.name)?.descriptor
        ?.name,
    width: '200px',
    showWhen: '(min-width: 1180px)',
  };

  private processActualityColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'actuality',
    name: 'Actuality',
    description: 'Whether the process is running from the currently active instance version',
    hint: BdDataColumnTypeHint.DETAILS,
    component: ProcessOutdatedComponent,
    data: (r) => {
      const procTag = ProcessesService.get(this.processes.processStates$.value, r.id)?.instanceTag;
      const instTag = this.instances.active$.value?.instance?.tag;
      return procTag === instTag ? null : procTag;
    }, // for sorting and display on the card.
    icon: () => 'thermostat',
    width: '70px',
  };

  private processStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'status',
    name: 'Status',
    hint: BdDataColumnTypeHint.STATUS,
    component: ProcessStatusIconComponent,
    data: (r) => ProcessesService.get(this.processes.processStates$.value, r.id)?.processState,
    width: '40px',
  };

  private processPortRatingColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'portStates',
    name: 'Ports',
    hint: BdDataColumnTypeHint.STATUS,
    component: PortStatusColumnComponent,
    data: (r) => this.getAllPortsRating(r),
    width: '40px',
  };

  public defaultProcessesColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.processStatusColumn,
    this.processPortRatingColumn,
    this.processActualityColumn,
  ];

  public defaultProcessClientColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processNameAndOsColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
  ];

  public defaultProcessesConfigColumns: BdDataColumn<ApplicationConfiguration>[] = [
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
  ];

  private getAllPortsRating(r: ApplicationConfiguration) {
    const currentStates = this.ports.activePortStates$.value;
    const processState = ProcessesService.get(this.processes.processStates$.value, r.id);
    if (!currentStates || !processState) {
      return undefined;
    }

    const appPorts = currentStates.filter((p) => p.appId === r.id);
    if (ProcessesService.isRunning(processState.processState)) {
      // process running, all ports should be open.
      return appPorts.every((p) => p.state);
    } else {
      // process not running, all ports should be closed.
      return appPorts.every((p) => !p.state);
    }
  }
}
