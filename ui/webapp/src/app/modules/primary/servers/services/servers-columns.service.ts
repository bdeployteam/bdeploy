import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataSyncCellComponent } from '../../../core/components/bd-data-sync-cell/bd-data-sync-cell.component';

@Injectable({
  providedIn: 'root',
})
export class ServersColumnsService {
  private readonly serverNameColumn: BdDataColumn<ManagedMasterDto, string> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.hostName,
    isId: true,
    hint: BdDataColumnTypeHint.TITLE,
  };

  private readonly serverDescColumn: BdDataColumn<ManagedMasterDto, string> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.description,
    hint: BdDataColumnTypeHint.DESCRIPTION,
  };

  private readonly serverNodesColumn: BdDataColumn<ManagedMasterDto, number> = {
    id: 'nodeCount',
    name: 'Nodes',
    data: (r) => Object.keys(r.nodes.nodes).length,
    icon: () => 'dock',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  private readonly serverSyncTimeColumn: BdDataColumn<ManagedMasterDto, number> = {
    id: 'syncTime',
    name: 'Last Sync.',
    data: (r) => r.lastSync,
    icon: () => 'history',
    hint: BdDataColumnTypeHint.DETAILS,
    width: '155px',
    component: BdDataDateCellComponent,
  };

  private readonly serverSyncColumn: BdDataColumn<ManagedMasterDto, ManagedMasterDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => r,
    component: BdDataSyncCellComponent,
    width: '50px',
  };

  public readonly defaultServerColumns: BdDataColumn<ManagedMasterDto, unknown>[] = [
    this.serverNameColumn,
    this.serverDescColumn,
    this.serverNodesColumn,
    this.serverSyncTimeColumn,
    this.serverSyncColumn,
  ];

  public readonly defaultReducedServerColumns: BdDataColumn<ManagedMasterDto, unknown>[] = [
    this.serverNameColumn,
    this.serverDescColumn,
  ];
}
