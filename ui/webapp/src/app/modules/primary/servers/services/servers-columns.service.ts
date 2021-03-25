import { Injectable } from '@angular/core';
import { format } from 'date-fns';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ServersService } from './servers.service';

@Injectable({
  providedIn: 'root',
})
export class ServersColumnsService {
  serverNameColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.hostName,
    hint: BdDataColumnTypeHint.TITLE,
  };

  serverDescColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.description,
    hint: BdDataColumnTypeHint.DESCRIPTION,
  };

  serverNodesColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'nodeCount',
    name: 'Nodes',
    data: (r) => Object.keys(r.minions.minions).length,
    icon: (r) => 'dock',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  serverSyncTimeColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'syncTime',
    name: 'Last Sync.',
    data: (r) => format(new Date(r.lastSync), 'dd.MM.yyyy HH:mm'),
    icon: (r) => 'history',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  serverSyncColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => `Synchronize ${r.hostName}`,
    action: (r) => this.servers.synchronize(r).subscribe(),
    classes: (r) => (this.servers.isSynchronized(r) ? [] : ['bd-warning-text']),
    icon: (r) => 'history',
    width: '50px',
  };

  public defaultServerColumns: BdDataColumn<ManagedMasterDto>[] = [
    this.serverNameColumn,
    this.serverDescColumn,
    this.serverNodesColumn,
    this.serverSyncTimeColumn,
    this.serverSyncColumn,
  ];

  constructor(private servers: ServersService) {}
}
