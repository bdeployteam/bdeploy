import { Component, inject, OnInit } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ManagedMasterDto, MinionNodeType, NodeListDto } from 'src/app/models/gen.dtos';
import {
  BdDataSvgIconCellComponent
} from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../services/server-details.service';
import { ServerNodeNameCellComponent } from './server-node-name-cell/server-node-name-cell.component';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { MinionRecord, NodesAdminService } from '../../../../primary/admin/services/nodes-admin.service';

export interface MinionRow extends MinionRecord {
  version: string;
}

const detailNameCol: BdDataColumn<MinionRow, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  component: ServerNodeNameCellComponent
};

const detailUrlCol: BdDataColumn<MinionRow, string> = {
  id: 'url',
  name: 'Local URL',
  data: (r) => r.status.config.remote.uri
};

const detailMasterCol: BdDataColumn<MinionRow, string> = {
  id: 'master',
  name: 'Master',
  data: (r) => (r.status.config.master ? 'Yes' : ''),
  width: '60px'
};

const detailVersionCol: BdDataColumn<MinionRow, string> = {
  id: 'version',
  name: 'Version',
  data: (r) => r.version,
  width: '150px'
};

const detailOsCol: BdDataColumn<MinionRow, string> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.status.config.os,
  component: BdDataSvgIconCellComponent,
  width: '30px'
};

const detailsMultiNodeCol: BdDataColumn<MinionRow, string> = {
  id: 'multiNode',
  name: 'Multi-Node',
  data: (r) => r.parentMultiNode,
  width: '90px'
};

const detailNodeTypeCol: BdDataColumn<MinionRow, MinionNodeType> = {
  id: 'type',
  name: 'Type',
  data: (r) => r.status.config.minionNodeType,
  width: '150px'
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
    BdDataGroupingComponent
  ]
})
export class ServerNodesComponent implements OnInit {
  private readonly servers = inject(ServersService);
  private readonly serverDetails = inject(ServerDetailsService);

  protected columns = [detailNameCol, detailNodeTypeCol, detailsMultiNodeCol, detailUrlCol, detailVersionCol, detailMasterCol, detailOsCol];
  protected minions: MinionRow[];
  protected server: ManagedMasterDto;

  protected loading$ = combineLatest([this.servers.loading$, this.serverDetails.loading$]).pipe(
    map(([a, b]) => a || b)
  );


  protected groupingDefinition: BdDataGroupingDefinition<MinionRow>[] = [
    {
      name: 'Node Type',
      group: detailNodeTypeCol.data,
      associatedColumn: detailNodeTypeCol.id,
      sort: NodesAdminService.nodeTypeColumnSort
    },
    {
      name: 'Multi-Node',
      group: (r) => r.status.config.minionNodeType === MinionNodeType.MULTI_RUNTIME ? r.parentMultiNode : 'None',
      associatedColumn: detailsMultiNodeCol.id,
      sort: NodesAdminService.multiNodeColumnSort
    },
    {
      name: 'OS',
      group: (r) => r.status.config.os,
      associatedColumn: detailOsCol.id
    }
  ];

  protected defaultGrouping: BdDataGrouping<MinionRow>[] = [
    { definition: this.groupingDefinition[0], selected: [] }
  ];

  protected grouping: BdDataGrouping<MinionRow>[] = [];

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
    return Object.keys(server.nodes.nodes).map((k) => {
      const dto = server.nodes.nodes[k];
      return {
        name: k,
        status: dto,
        version: convert2String(dto.config.version),
        parentMultiNode: this.findParentFor(k, server.nodes)
      };
    });
  }

  private findParentFor(name: string, node: NodeListDto): string {
    for (const key of Object.keys(node.multiNodeToRuntimeNodes)) {
      if (node.multiNodeToRuntimeNodes[key].includes(name)) {
        return key;
      }
    }
    return null;
  }
}
