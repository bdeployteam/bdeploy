import { Component, inject, OnInit } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ManagedMasterDto, OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../services/server-details.service';
import { ServerNodeNameCellComponent } from './server-node-name-cell/server-node-name-cell.component';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';

export interface MinionRow {
  name: string;
  os: OperatingSystem;
  master: boolean;
  version: string;
  url: string;
}

const detailNameCol: BdDataColumn<MinionRow> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  component: ServerNodeNameCellComponent,
};

const detailUrlCol: BdDataColumn<MinionRow> = {
  id: 'url',
  name: 'Local URL',
  data: (r) => r.url,
};

const detailMasterCol: BdDataColumn<MinionRow> = {
  id: 'master',
  name: 'Master',
  data: (r) => (r.master ? 'Yes' : ''),
  width: '60px',
};

const detailVersionCol: BdDataColumn<MinionRow> = {
  id: 'version',
  name: 'Version',
  data: (r) => r.version,
  width: '150px',
};

const detailOsCol: BdDataColumn<MinionRow> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.os,
  component: BdDataSvgIconCellComponent,
  width: '30px',
};

@Component({
    selector: 'app-server-nodes',
    templateUrl: './server-nodes.component.html',
    providers: [ServerDetailsService],
    imports: [
        BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        BdDataDisplayComponent,
        BdNoDataComponent,
    ],
})
export class ServerNodesComponent implements OnInit {
  private readonly servers = inject(ServersService);
  private readonly serverDetails = inject(ServerDetailsService);

  protected columns = [detailNameCol, detailUrlCol, detailVersionCol, detailMasterCol, detailOsCol];
  protected minions: MinionRow[];
  protected server: ManagedMasterDto;

  protected loading$ = combineLatest([this.servers.loading$, this.serverDetails.loading$]).pipe(
    map(([a, b]) => a || b),
  );

  ngOnInit(): void {
    this.serverDetails.server$.subscribe((server) => {
      if (!server) {
        return;
      }
      this.server = server;
      this.minions = this.getMinionRecords(server);
    });
  }

  private getMinionRecords(server: ManagedMasterDto): MinionRow[] {
    return Object.keys(server.minions.minions).map((k) => {
      const dto = server.minions.minions[k];
      return {
        name: k,
        os: dto.os,
        master: dto.master,
        version: convert2String(dto.version),
        url: dto.remote.uri,
      };
    });
  }
}
