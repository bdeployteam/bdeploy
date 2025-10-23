import { inject, Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ApplicationConfiguration, ApplicationStartType } from 'src/app/models/gen.dtos';
import {
  BdIdentifierCellComponent
} from 'src/app/modules/core/components/bd-identifier-cell/bd-identifier-cell.component';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessOutdatedComponent } from '../components/dashboard/process-outdated/process-outdated.component';
import {
  ProcessStarttypeIconComponent
} from '../components/dashboard/process-starttype-icon/process-starttype-icon.component';
import { ProcessStatusIconComponent } from '../components/dashboard/process-status-icon/process-status-icon.component';
import { PortStatusColumnComponent } from '../components/port-status-column/port-status-column.component';
import { ProcessNameAndOsComponent } from '../components/process-name-and-os/process-name-and-os.component';
import { InstancesService } from './instances.service';
import { ProcessesService } from './processes.service';
import { PortsService } from './ports.service';

/**
 * The purpose of this is to enrich ApplicationConfiguration with information about how you want this displayed.
 * This process list component (which is responsible for displaying a list of processes) is used to display processes from either:
 * - in a multi-node where you to display the composite version
 * - from a server node where you can be sure that 1 app = 1 node
 * - from a multi-node dashboard where you want to list each process for each node in a multi node
 */
export interface ProcessDisplayData extends ApplicationConfiguration {
  serverNode?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProcessesColumnsService {
  private readonly processes = inject(ProcessesService);
  private readonly instances = inject(InstancesService);
  private readonly ports = inject(PortsService);

  private readonly processNameColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name
  };

  private readonly processNameAndOsColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'nameAndOs',
    name: 'Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    component: ProcessNameAndOsComponent
  };

  private readonly processIdColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => (r.id ? r.id : 'new'),
    isId: true,
    width: '122px',
    showWhen: '(min-width:1000px)',
    component: BdIdentifierCellComponent
  };

  private readonly processAvatarColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'os-avatar',
    name: 'OS',
    hint: BdDataColumnTypeHint.AVATAR,
    data: (r) => `/assets/${getAppOs(r.application).toLowerCase()}.svg`,
    display: BdDataColumnDisplay.CARD
  };

  public readonly applicationNameColumn: BdDataColumn<ApplicationConfiguration, string> = {
    id: 'appName',
    name: 'Application Type',
    data: (r) =>
      this.instances.activeNodeCfgs$.value?.applications?.find((app) => app.key.name === r.application.name)?.descriptor
        ?.name,
    width: '200px',
    showWhen: '(min-width: 1180px)'
  };

  private readonly processActualityColumn: BdDataColumn<ProcessDisplayData, string> = {
    id: 'actuality',
    name: 'Actuality',
    description: 'Whether the process is running from the currently active instance version',
    hint: BdDataColumnTypeHint.DETAILS,
    component: ProcessOutdatedComponent,
    data: (r) => {
      const procTag = ProcessesService.get(this.processes.processStates$.value, r.id, r.serverNode)?.instanceTag;
      const instTag = this.instances.active$.value?.instance?.tag;
      return procTag === instTag ? null : procTag;
    }, // for sorting and display on the card.
    icon: () => 'thermostat',
    width: '70px'
  };

  public readonly startTypeColumn: BdDataColumn<ApplicationConfiguration, ApplicationStartType> = {
    id: 'startType',
    name: 'Start Type',
    display: BdDataColumnDisplay.TABLE,
    component: ProcessStarttypeIconComponent,
    data: (r) => r.processControl.startType,
    width: '40px'
  };

  private readonly processStatusColumn: BdDataColumn<ProcessDisplayData, string> = {
    id: 'status',
    name: 'Status',
    hint: BdDataColumnTypeHint.STATUS,
    component: ProcessStatusIconComponent,
    data: (r) => ProcessesService.get(this.processes.processStates$.value, r.id, r.serverNode)?.processState,
    width: '40px'
  };

  private readonly processPortRatingColumn: BdDataColumn<ProcessDisplayData, boolean> = {
    id: 'portStates',
    name: 'Ports',
    hint: BdDataColumnTypeHint.STATUS,
    component: PortStatusColumnComponent,
    data: (r) => this.getAllPortsRating(r),
    width: '40px'
  };

  public readonly defaultProcessesColumns: BdDataColumn<ProcessDisplayData, unknown>[] = [
    this.processNameColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.startTypeColumn,
    this.processStatusColumn,
    this.processPortRatingColumn,
    this.processActualityColumn
  ];

  public readonly defaultProcessClientColumns: BdDataColumn<ApplicationConfiguration, unknown>[] = [
    this.processNameAndOsColumn,
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn
  ];

  public readonly defaultProcessesConfigColumns: BdDataColumn<ApplicationConfiguration, unknown>[] = [
    this.processIdColumn,
    this.processAvatarColumn,
    this.applicationNameColumn,
    this.startTypeColumn
  ];

  private getAllPortsRating(r: ProcessDisplayData): boolean {
    // appPorts will have the map of server nodes (or single node) and a status check for each + the process state.
    // That duplicates the process state for each port, but will ultimately be the same value for each
    // corresponding server node entry
    const appPorts = this.ports.activePortStates$.value?.[r.id]?.portStates;
    return appPorts?.flatMap(compositeState => compositeState.states)
      //if we have a server node configured then we check only that, OWISE all nodes
      .filter(portState => r.serverNode ? (portState.serverNode === r.serverNode) : true)
      .every(portState => PortsService.isOk(portState));
  }
}
