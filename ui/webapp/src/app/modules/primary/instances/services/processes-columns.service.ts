import { Injectable, inject } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ApplicationConfiguration, ApplicationStartType } from 'src/app/models/gen.dtos';
import { BdIdentifierCellComponent } from 'src/app/modules/core/components/bd-identifier-cell/bd-identifier-cell.component';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessOutdatedComponent } from '../components/dashboard/process-outdated/process-outdated.component';
import { ProcessStarttypeIconComponent } from '../components/dashboard/process-starttype-icon/process-starttype-icon.component';
import { ProcessStatusIconComponent } from '../components/dashboard/process-status-icon/process-status-icon.component';
import { PortStatusColumnComponent } from '../components/port-status-column/port-status-column.component';
import { ProcessNameAndOsComponent } from '../components/process-name-and-os/process-name-and-os.component';
import { InstancesService } from './instances.service';
import { ProcessesService } from './processes.service';
import { PortsService } from './ports.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesColumnsService {
  private readonly processes = inject(ProcessesService);
  private readonly instances = inject(InstancesService);
  private readonly ports = inject(PortsService);

  private readonly processNameColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
  };

  private readonly processNameAndOsColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'nameAndOs',
    name: 'Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    component: ProcessNameAndOsComponent,
  };

  private readonly processIdColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => (r.id ? r.id : 'new'),
    isId: true,
    width: '122px',
    showWhen: '(min-width:1000px)',
    component: BdIdentifierCellComponent,
  };

  private readonly processAvatarColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'os-avatar',
    name: 'OS',
    hint: BdDataColumnTypeHint.AVATAR,
    data: (r) => `/assets/${getAppOs(r.application).toLowerCase()}.svg`,
    display: BdDataColumnDisplay.CARD,
  };

  public readonly applicationNameColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'appName',
    name: 'Application Type',
    data: (r) =>
      this.instances.activeNodeCfgs$.value?.applications?.find((app) => app.key.name === r.application.name)?.descriptor
        ?.name,
    width: '200px',
    showWhen: '(min-width: 1180px)',
  };

  private readonly processActualityColumn: BdDataColumn<ApplicationConfiguration, string> = {
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

  public readonly startTypeColumn: BdDataColumn<ApplicationConfiguration, ApplicationStartType> = {
    id: 'startType',
    name: 'Start Type',
    display: BdDataColumnDisplay.TABLE,
    component: ProcessStarttypeIconComponent,
    data: (r) => r.processControl.startType,
    width: '40px',
  };

  private readonly processStatusColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'status',
    name: 'Status',
    hint: BdDataColumnTypeHint.STATUS,
    component: ProcessStatusIconComponent,
    data: (r) => ProcessesService.get(this.processes.processStates$.value, r.id)?.processState,
    width: '40px',
  };

  private readonly processPortRatingColumn: BdDataColumn<ApplicationConfiguration, boolean> = {
    id: 'portStates',
    name: 'Ports',
    hint: BdDataColumnTypeHint.STATUS,
    component: PortStatusColumnComponent,
    data: (r) => this.getAllPortsRating(r),
    width: '40px',
  };

  public readonly defaultProcessesColumns: BdDataColumn<ApplicationConfiguration, unknown>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.startTypeColumn,
    this.processStatusColumn,
    this.processPortRatingColumn,
    this.processActualityColumn,
  ];

  public readonly defaultProcessClientColumns: BdDataColumn<ApplicationConfiguration, unknown>[] = [
    this.processNameAndOsColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
  ];

  public readonly defaultProcessesConfigColumns: BdDataColumn<ApplicationConfiguration, unknown>[] = [
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.startTypeColumn,
  ];

  private getAllPortsRating(r: ApplicationConfiguration): boolean {
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
