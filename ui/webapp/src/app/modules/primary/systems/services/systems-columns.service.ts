import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import {
  ManagedMasterDto,
  MinionMode,
  SystemConfigurationDto,
} from 'src/app/models/gen.dtos';
import { BdDataSyncCellComponent } from 'src/app/modules/core/components/bd-data-sync-cell/bd-data-sync-cell.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ServersService } from '../../servers/services/servers.service';

@Injectable({
  providedIn: 'root',
})
export class SystemsColumnsService {
  public systemIdColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'system',
    name: 'UUID',
    data: (r) => r.config.uuid,
    width: '120px',
    hint: BdDataColumnTypeHint.FOOTER,
    showWhen: '(min-width: 1000px)',
  };

  public systemNameColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.config.name,
    hint: BdDataColumnTypeHint.TITLE,
  };

  public systemDescriptionColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.config.description,
    hint: BdDataColumnTypeHint.DESCRIPTION,
  };

  public systemMinionColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'minion',
    name: 'Server',
    data: (r) => r.minion,
    width: '250px',
    icon: () => 'dns',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  public systemSyncColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => this.getServer(r.minion),
    component: BdDataSyncCellComponent,
    width: '50px',
  };

  public systemVarsColumn: BdDataColumn<SystemConfigurationDto> = {
    id: 'vars',
    name: '# Variables',
    data: (r) => Object.keys(r.config.systemVariables).length,
    width: '100px',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  private servers: ManagedMasterDto[] = [];

  constructor(cfg: ConfigService, servers: ServersService) {
    if (cfg.config.mode !== MinionMode.CENTRAL) {
      return;
    }
    servers.servers$.subscribe((s) => (this.servers = s));
  }

  private getServer(name: string): ManagedMasterDto {
    return this.servers.find((s) => s.hostName === name);
  }
}
